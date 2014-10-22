package sample.simple.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HelloWorldService {
	public String getHelloMessage() {
		return "Hello!";
	}
}
