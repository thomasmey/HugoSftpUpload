package de.m3y3r.hsu;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonString;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

public class Uploader implements Runnable {

	final static Logger logger = Logger.getLogger(Uploader.class.getName());
	final JsonObject config;
	final JsonObject comparisonResult;

	final Session session;
	final ChannelSftp channel;

	Uploader(JsonObject config, JsonObject comparisonResult) throws JSchException {
		this.config = config;
		this.comparisonResult = comparisonResult;

		JsonObject sc = config.getJsonObject("sftp");
		session = (new JSch()).getSession(sc.getString("username"), sc.getString("hostname"), sc.getInt("port", 22));
		session.setPassword(sc.getString("password"));
		session.setConfig("StrictHostKeyChecking", "no");
		session.connect();

		channel = (ChannelSftp) session.openChannel("sftp");
		channel.connect();
	}

	public void uploadFile(File fp, String destination) {

		try {
			channel.put(new FileInputStream(fp), destination);
		} catch (FileNotFoundException | SftpException e) {
			logger.log(Level.INFO, "Failed to upload {0} to dest {1}", new Object[] {fp, destination});
		}
	}

	public void deleteFile(String destination) {
		//TODO: what about the directories?
		try {
			channel.rm(destination);
		} catch (SftpException e) {
			logger.log(Level.INFO, "Failed to delete file {0}", destination);
		}
	}

	private Set<String> alreadyCreatedDirectories = new HashSet<>();

	private void createDirectory(final String destination) {
		if(alreadyCreatedDirectories.contains(destination))
			return;

		try {
			channel.mkdir(destination);
		} catch (SftpException e) {
			if("No such file".equals(e.getMessage())) {
				int i = destination.lastIndexOf('/');
				if (i >= 0) {
					createDirectory(destination.substring(0, i));
				}
			} else if("File already exists".equals(e.getMessage())) {
				alreadyCreatedDirectories.add(destination);
			}
			logger.log(Level.WARNING, "failed to create directory {0} on remote side: {1}", new Object[] {destination, e.getMessage()});
		}
	}

	public static void main(String... args) throws JSchException, FileNotFoundException {
		String configFile;
		if(args.length > 0) {
			configFile = args[0];
		} else {
			configFile = "config.json";
		}
		JsonObject config = Json.createReader(new FileInputStream(configFile)).readObject();

		String comparisonDir;
		if(args.length > 1) {
			comparisonDir = args[1];
		} else {
			comparisonDir = System.getProperty("user.dir");
		}
		File f = new File(comparisonDir);
		File[] comparisonFiles = f.listFiles((d, n) -> { if(n.startsWith(HashComparer.HASH_COMPARSION_RESULT_NAME)) return true; else return false;});
		Arrays.sort(comparisonFiles, (o, n) -> { return -(o.getName().compareTo(n.getName()));});

		JsonObject comparisonResult = Json.createReader(new FileInputStream(comparisonFiles[0])).readObject();
		new Uploader(config, comparisonResult).run();
	}

	private String getDestination(File f) {
		File baseSource = new File(config.getJsonObject("hugo").getString("publicdir"));
		String baseDestination = config.getJsonObject("sftp").getString("targetdir");

		Path rp = baseSource.toPath().relativize(f.toPath());
		String pt = rp.toString().replace(FileSystems.getDefault().getSeparator(), "/");
		pt = baseDestination + pt;
		return pt;
	}

	private void close() {
		channel.disconnect();
		session.disconnect();
	}

	@Override
	public void run() {
		logger.log(Level.INFO, "Start uploaded {0} new files", comparisonResult.getJsonArray("new").size());
		for(JsonString fn: comparisonResult.getJsonArray("new").getValuesAs(JsonString.class)) {
			File f = new File(fn.getString());
			String dest = getDestination(f);
			String destDir = dest.substring(0, dest.lastIndexOf('/'));
			createDirectory(destDir);
			uploadFile(f, dest);
		}

		logger.log(Level.INFO, "Start uploaded {0} changed files", comparisonResult.getJsonArray("changed").size());
		for(JsonString fn: comparisonResult.getJsonArray("changed").getValuesAs(JsonString.class)) {
			File f = new File(fn.getString());
			uploadFile(f, getDestination(f));
		}

		logger.log(Level.INFO, "Start removing {0} deleted files", comparisonResult.getJsonArray("deleted").size());
		for(JsonString fn: comparisonResult.getJsonArray("deleted").getValuesAs(JsonString.class)) {
			File f = new File(fn.getString());
			deleteFile(getDestination(f));
		}

		close();
	}
}
