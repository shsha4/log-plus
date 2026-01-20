package com.log.plus

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PlusApplication

fun main(args: Array<String>) {
	runApplication<PlusApplication>(*args)
}
