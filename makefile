
deploy_grid5000: clean
	docker build ./ -t working-box
	docker save working-box:latest -o working-box_latest.tar
	scp working-box_latest.tar g5k:nantes/

clean:
	rm -f working-box_latest.tar



deploy_docker:
	docker run -ti -e COLLECTOR_ZIPKIN_HTTP_PORT=9411 -p 5775:5775/udp -p 6831:6831/udp -p 6832:6832/udp -p 5778:5778 -p 16686:16686 -p 14268:14268 -p 14250:14250 -p 9411:9411 jaegertracing/all-in-one:1.17

deploy_envoy:
	docker run -ti -p 80:80 front-envoy:latest

deploy_service1:
	JAEGER_ENDPOINT=http://192.168.99.100:14268/api/traces SPRING_APPLICATION_NAME=working-box1 SERVER_PORT=8080 BOX_POLYNOME_COEFFICIENTS=1000.,10.,100. BOX_REMOTE_CALLS=http://localhost:8081@80,http://localhost:8082@80 mvn spring-boot:run

deploy_service2:
	JAEGER_ENDPOINT=http://192.168.99.100:14268/api/traces SPRING_APPLICATION_NAME=working-box2 SERVER_PORT=8081 BOX_POLYNOME_COEFFICIENTS=100,0,10 BOX_REMOTE_CALLS=http://localhost:8082@160 mvn spring-boot:run

deploy_service3:
	JAEGER_ENDPOINT=http://192.168.99.100:14268/api/traces SPRING_APPLICATION_NAME=working-box3 SERVER_PORT=8082 BOX_POLYNOME_COEFFICIENTS=100,0,20 BOX_REMOTE_CALLS=http://localhost:8083@160 mvn spring-boot:run

