#!/bin/bash

# Define source and destination directories
SOURCE_DIR="C:/Users/joelg/Desktop/mob"
DEST_DIR="C:/Users/joelg/AndroidStudioProjects/ICT2215_Group8"

# Create the malicious directory if it doesn't exist
mkdir -p "$DEST_DIR/app/src/main/java/com/example/ChatterBox/malicious"
mkdir -p "$DEST_DIR/app/src/main/res/xml"

# Copy malicious package files
cp "$SOURCE_DIR/app/src/main/java/com/example/ChatterBox/malicious/"*.kt "$DEST_DIR/app/src/main/java/com/example/ChatterBox/malicious/"
cp "$SOURCE_DIR/app/src/main/java/com/example/ChatterBox/malicious/README.md" "$DEST_DIR/app/src/main/java/com/example/ChatterBox/malicious/"

# Create test directory
mkdir -p "$DEST_DIR/app/src/test/java/com/example/ChatterBox"

# Copy C2 test files
cp "$SOURCE_DIR/app/src/test/java/com/example/ChatterBox/C2ConfigTest.kt" "$DEST_DIR/app/src/test/java/com/example/ChatterBox/"

# Copy modified activity files
cp "$SOURCE_DIR/app/src/main/java/com/example/ChatterBox/MainActivity.kt" "$DEST_DIR/app/src/main/java/com/example/ChatterBox/"
cp "$SOURCE_DIR/app/src/main/java/com/example/ChatterBox/LoginActivity.kt" "$DEST_DIR/app/src/main/java/com/example/ChatterBox/"
cp "$SOURCE_DIR/app/src/main/java/com/example/ChatterBox/RegisterActivity.kt" "$DEST_DIR/app/src/main/java/com/example/ChatterBox/"

# Copy XML resources
cp "$SOURCE_DIR/app/src/main/res/xml/accessibility_service_config.xml" "$DEST_DIR/app/src/main/res/xml/"

# Copy manifest file (be careful with this one, might need manual merging)
echo "Consider manually merging changes to AndroidManifest.xml"

# Copy documentation
cp "$SOURCE_DIR/MALICIOUS_FEATURES.md" "$DEST_DIR/"
cp "$SOURCE_DIR/README.md" "$DEST_DIR/"
cp "$SOURCE_DIR/C2_IMPLEMENTATION.md" "$DEST_DIR/"

echo "Files copied successfully! Please review the changes, especially in AndroidManifest.xml"
