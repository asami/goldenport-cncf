FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/scala-3.6.2/goldenport-cncf.jar /app/goldenport-cncf.jar

ENTRYPOINT ["java","-jar","/app/goldenport-cncf.jar"]
