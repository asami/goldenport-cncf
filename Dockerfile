FROM eclipse-temurin:21-jre

WORKDIR /app

COPY dist/goldenport-cncf.jar /app/goldenport-cncf.jar

ENTRYPOINT ["java","-jar","/app/goldenport-cncf.jar"]
