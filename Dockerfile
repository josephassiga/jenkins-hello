FROM ubuntu:18.04
RUN apt-get update
RUN apt-get install -y curl

COPY . /app
RUN echo $MATIS_ECO_APP_VERSION
RUN printenv
#RUN curl -k -u $username:$password -X GET "https://artifacts.cloud.safran/repository/safranae-portfolioopmatiseco-matis-me-int/MatisEco/SystemAPI/DBM/MatisEco.SystemAPI.DBM.$MATIS_ECO_APP_VERSION".tgz > build.tgz

RUN curl -s -X GET https://storage.googleapis.com/kubernetes-release/${MATIS_ECO_APP_VERSION}/stable.txt > stable.txt 
RUN cat stable.txt
RUN ls -al

ENTRYPOINT ["curl"]