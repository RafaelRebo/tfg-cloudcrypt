FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY cloudcrypt-controller/target/cloudcrypt*.jar /app/cloudcrypt.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "cloudcrypt.jar"]