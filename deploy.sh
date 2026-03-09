#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# --- Configuration ---
S3_BUCKET="rosterportal"
LOCAL_JAR_PATH="target/roster-portal-java-1.0.0-SNAPSHOT.jar"
REMOTE_JAR_NAME="app.jar"
ASG_NAME="rw-stack-InstanceSecurityGroup-mWMYoNZunzeG"

echo "Generating mkdocs documentation..."
mkdocs build --strict -d src/main/resources/static/docs

# 1. Build the application using Maven
echo "Building the application with Maven..."
mvn clean install -Dheadless.mode=true --batch-mode --no-transfer-progress -DskipTests

# 2. Upload the JAR to S3
# This command will only run if the 'mvn clean install' command is successful.
echo "Uploading JAR to S3 bucket: $S3_BUCKET..."
aws s3 cp "$LOCAL_JAR_PATH" "s3://$S3_BUCKET/$REMOTE_JAR_NAME" --no-progress

# 3. Force the Auto Scaling group to refresh
# This will start replacing the instance in the ASG.
# It will use the latest launch template/configuration defined for the ASG.
echo "Starting instance refresh for Auto Scaling group: $ASG_NAME..."
aws autoscaling start-instance-refresh --auto-scaling-group-name "$ASG_NAME" --region ap-southeast-2 --preferences "MinHealthyPercentage=100,MaxHealthyPercentage=110"

echo "Deployment process initiated successfully!"