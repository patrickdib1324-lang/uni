# How the cloud builds and runs your app.
# (Comments must be on their OWN lines in a Dockerfile.)

# A ready-made Linux box that already has Java 17.
FROM eclipse-temurin:17-jdk

# Work inside the /app folder.
WORKDIR /app

# Copy your whole project in.
COPY . .

# Compile the server (UTF-8 so the emojis in the code don't break the build).
RUN javac -encoding UTF-8 -cp "lib/postgresql.jar" -d out server/Server.java

# Start the server when the container runs.
CMD ["sh", "-c", "java -cp \"out:lib/postgresql.jar\" Server"]
