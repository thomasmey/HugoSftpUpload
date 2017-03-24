package de.m3y3r.hsu;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

import com.jcraft.jsch.JSchException;

public class HashComparer implements Runnable {

	final static Logger logger = Logger.getLogger(HashComparer.class.getName());

	public static final String HASH_COMPARSION_RESULT_NAME = "hashesComparison.";

	final JsonObject config;
	final Properties propsOld, propsNew;

	public HashComparer(JsonObject config, String oldHashes, String newHashes) throws IOException {
		this.config = config;
		propsOld = new Properties();
		propsNew = new Properties();

		propsOld.load(new FileInputStream(oldHashes));
		propsNew.load(new FileInputStream(newHashes));
	}

	@Override
	public void run() {

		List<File> changed = new ArrayList<>();
		List<File> newF = new ArrayList<>();
		List<File> deleted = new ArrayList<>();

		// find new and changed files
		for(Entry<Object, Object> e : propsNew.entrySet()) {
			String fileNew = (String) e.getKey();
			String hashNew = (String) e.getValue();

			if(!propsOld.containsKey(fileNew)) {
				logger.log(Level.FINE, "Found new file {0}", fileNew);
				// file is new
				File f = new File(fileNew);
				newF.add(f);
			} else {
				String hashOld = propsOld.getProperty(fileNew);
				if(!hashNew.equals(hashOld)) {
					logger.log(Level.FINE, "Found changed file {0}", fileNew);
					// file did change
					File f = new File(fileNew);
					changed.add(f);
				}
			}
		}

		// find deleted files
		for(Entry<Object, Object> e : propsOld.entrySet()) {
			String fileOld = (String) e.getKey();
			String hashOld = (String) e.getValue();

			if(!propsNew.containsKey(fileOld)) {
				logger.log(Level.FINE, "Found deleted file {0}", fileOld);
				File f = new File(fileOld);
				deleted.add(f);
			}
		}

		logger.log(Level.INFO, "Found {0}\tnew files", newF.size());
		logger.log(Level.INFO, "Found {0}\tchanged files", changed.size());
		logger.log(Level.INFO, "Found {0}\tdeleted files", deleted.size());

		// convert and store to disk
		JsonArrayBuilder nob = Json.createArrayBuilder();
		JsonArrayBuilder cob = Json.createArrayBuilder();
		JsonArrayBuilder dob = Json.createArrayBuilder();
		newF.forEach(f -> nob.add(f.getAbsolutePath()));
		changed.forEach(f -> cob.add(f.getAbsolutePath()));
		deleted.forEach(f -> dob.add(f.getAbsolutePath()));

		JsonObject result = Json.createObjectBuilder()
				.add("new", nob)
				.add("changed", cob)
				.add("deleted", dob)
				.build();
		try {
			Json.createWriter(new FileOutputStream(HASH_COMPARSION_RESULT_NAME + System.currentTimeMillis() + ".json")).writeObject(result);
		} catch (FileNotFoundException e) {
		}
	}

	/**
	 * args[0] - config.json file name or null
	 * args[1] - dir containing filesToHashes files, or null
	 * @param args
	 * @throws JSchException
	 * @throws IOException
	 */
	public static void main(String[] args) throws JSchException, IOException {

		String configFile;
		if(args.length > 0) {
			configFile = args[0];
		} else {
			configFile = "config.json";
		}
		JsonObject config = Json.createReader(new FileInputStream(configFile)).readObject();

		String hashesDir;
		if(args.length > 1) {
			hashesDir = args[1];
		} else {
			hashesDir = System.getProperty("user.dir");
		}
		File f = new File(hashesDir);
		File[] hashFiles = f.listFiles((d, n) -> { if(n.startsWith(FileHasher.HASHER_RESULT_NAME)) return true; else return false;});
		Arrays.sort(hashFiles, (o, n) -> { return -(o.getName().compareTo(n.getName()));});

		String oldHashes = hashFiles[1].getAbsolutePath();
		String newHashes = hashFiles[0].getAbsolutePath();

		new HashComparer(config, oldHashes, newHashes).run();
	}
}
