package de.m3y3r.hsu;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.xml.bind.DatatypeConverter;

public class FileHasher implements Runnable {

	final static Logger logger = Logger.getLogger(FileHasher.class.getName());

	public static final String HASHER_RESULT_NAME = "fileNamesToHashes.";

	final JsonObject config;

	final MessageDigest md;
	final byte[] buffer = new byte[8192];
	final long runId;
	final Map<File, byte[]> fileHashes;

	FileHasher(JsonObject config) throws NoSuchAlgorithmException {
		md = MessageDigest.getInstance("SHA-1");
		runId = System.currentTimeMillis();
		fileHashes = new HashMap<>();
		this.config = config;
	}

	public static void main(String... args) throws NoSuchAlgorithmException, FileNotFoundException {
		String configFile;
		if(args.length > 0) {
			configFile = args[0];
		} else {
			configFile = "config.json";
		}
		JsonObject config = Json.createReader(new FileInputStream(configFile)).readObject();
		new FileHasher(config).run();
	}

	public void run() {
		File startFile = new File(config.getJsonObject("hugo").getString("publicdir"));
		long start = System.currentTimeMillis();
		processFile(startFile);
		long duration = System.currentTimeMillis() - start;

		logger.log(Level.INFO, "Did hash {0} files in {1} miliseconds", new Object[] {fileHashes.size(), duration});

		// convert and store to disk
		Properties p = new Properties();
		for(Entry<File, byte[]> e: fileHashes.entrySet()) {
			p.setProperty(e.getKey().getAbsolutePath(), DatatypeConverter.printHexBinary(e.getValue()));
		}
		try {
			p.store(new FileOutputStream(new File(HASHER_RESULT_NAME + runId)), null);
		} catch (IOException e) {
			logger.severe("failed to store hashes result file");
		}
	}

	private void processFile(File fp) {
		if(fp.isFile()) {
			fileHashes.put(fp, hashFile(fp));
		} else if(fp.isDirectory()) {
			for(File f: fp.listFiles()) {
				processFile(f);
			}
		}
	}

	private byte[] hashFile(File fp) {
		md.reset();
		try(InputStream in = new FileInputStream(fp)) {
			int len = in.read(buffer);
			while(len >= 0) {;
				md.update(buffer, 0, len);
				len = in.read(buffer);
			}
			return md.digest();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
