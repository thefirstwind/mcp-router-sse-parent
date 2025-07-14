package com.nacos.mcp.server.v1.tools;

import com.nacos.mcp.server.v1.model.Person;
import com.nacos.mcp.server.v1.repository.PersonRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Person管理工具类
 * 使用Spring AI的@Tool注解实现MCP工具
 */
@Slf4j
@Component
public class PersonManagementTool {

    private final PersonRepository personRepository;

    public PersonManagementTool(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    /**
     * 获取所有人员
     */
    @Tool(name = "getAllPersons", description = "Get all persons from the database")
    public Flux<Person> getAllPersons() {
        log.info("PersonManagementTool#getAllPersons");
        Flux<Person> persons = personRepository.findAll();
        log.info("PersonManagementTool#getAllPersons persons: {}", persons);
        return persons;
    }

    /**
     * 添加人员
     */
    @Tool(name = "addPerson", description = "Add a new person to the database")
    public Mono<Person> addPerson(
            @ToolParam(description = "Person's first name") String firstName,
            @ToolParam(description = "Person's last name") String lastName,
            @ToolParam(description = "Person's age") Integer age,
            @ToolParam(description = "Person's nationality") String nationality,
            @ToolParam(description = "Person's gender (MALE, FEMALE, OTHER)") String gender) {
        log.info("PersonManagementTool#addPerson firstName: {}", firstName);
        log.info("PersonManagementTool#addPerson lastName: {}", lastName);
        log.info("PersonManagementTool#addPerson age: {}", age);
        log.info("PersonManagementTool#addPerson nationality: {}", nationality);
        log.info("PersonManagementTool#addPerson gender: {}", gender);
        Person person = new Person();
        person.setFirstName(firstName);
        person.setLastName(lastName);
        person.setAge(age);
        person.setNationality(nationality);
        person.setGender(Person.Gender.valueOf(gender.toUpperCase()));
        
        return personRepository.save(person);
    }

    /**
     * 删除人员
     */
    @Tool(name = "deletePerson", description = "Delete a person from the database")
    public Mono<Void> deletePerson(
            @ToolParam(description = "Person's ID") Long id) {
        log.info("PersonManagementTool#deletePerson id: {}", id);
        return personRepository.deleteById(id);
    }

    /**
     * 获取系统信息
     */
    @Tool(name = "get_system_info", description = "Get system information")
    public Map<String, Object> getSystemInfo() {
        log.info("PersonManagementTool#getSystemInfo");
        return Map.of(
                "server", "mcp-server-v1",
                "version", "1.0.0",
                "timestamp", System.currentTimeMillis(),
                "javaVersion", System.getProperty("java.version"),
                "osName", System.getProperty("os.name")
        );
    }

    /**
     * 列出服务器
     */
    @Tool(name = "list_servers", description = "List all registered servers")
    public Map<String, Object> listServers() {
        log.info("PersonManagementTool#listServers");
        return Map.of(
                "servers", List.of(
                        Map.of("name", "mcp-server-v1", "port", 8061, "status", "active")
                )
        );
    }
} 