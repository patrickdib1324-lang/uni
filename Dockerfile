# How the cloud builds and runs your app.
# Think of it as a recipe: "install Java, copy my files, compile, then run."

FROM eclipse-temurin:17-jdk          # a ready-made Linux box that already has Java 17

WORKDIR /app                         # work inside the /app folder on that box

COPY . .                             # copy your whole project into /app

# compile the server (Linux uses ":" between classpath parts, not ";")
RUN javac -cp "lib/postgresql.jar" -d out server/Server.java

# start the server when the container runs
CMD ["sh", "-c", "java -cp \"out:lib/postgresql.jar\" Server"]
