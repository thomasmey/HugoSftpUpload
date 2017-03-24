# Hugo SFTP upload tools

# Config
First create a config.json file in your working directory from the config.template.json file. adjust values accordingly.

# FileHasher
Hashes all files in the public directory.

	mvn exec:java -Dexec.mainClass=de.m3y3r.hsu.FileHasher

# HashComparer
Compares the last two FileHasher results

	mvn exec:java -Dexec.mainClass=de.m3y3r.hsu.HashComparer

# Uploader
Takes the latest HashComparer result files and sync remote side via SFTP

	mvn exec:java -Dexec.mainClass=de.m3y3r.hsu.Uploader

# Run order
1. FileHasher
2. hugo
3. FileHasher
4. HashComparer
5. Uploader

