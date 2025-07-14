package com.nacos.mcp.server.v2.tools;

import com.nacos.mcp.server.v2.model.Person;
import com.nacos.mcp.server.v2.repository.PersonRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public List<Map<String, Object>> getAllPersons() {
        log.info("PersonManagementTool#getAllPersons");
        List<Person> persons = personRepository.findAll().collectList().block();
        log.info("PersonManagementTool#getAllPersons found {} persons", persons != null ? persons.size() : 0);

        if (persons == null || persons.isEmpty()) {
            return List.of(Map.of("message", "No persons found", "count", 0));
        }

        return persons.stream()
                .map(person -> {
                    Map<String, Object> personMap = new HashMap<>();
                    personMap.put("id", person.getId());
                    personMap.put("firstName", person.getFirstName());
                    personMap.put("lastName", person.getLastName());
                    personMap.put("age", person.getAge());
                    personMap.put("nationality", person.getNationality());
                    personMap.put("gender", person.getGender().toString());
                    return personMap;
                })
                .collect(Collectors.toList());
    }

    /**
     * 根据ID获取人员
     */
    @Tool(name = "getPersonById", description = "Get a person by their ID")
    public Map<String, Object> getPersonById(
            @ToolParam(description = "Person's ID") Long id) {
        log.info("PersonManagementTool#getPersonById id: {}", id);
        try {
            Person person = personRepository.findById(id).block();
            if (person != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("id", person.getId());
                result.put("firstName", person.getFirstName());
                result.put("lastName", person.getLastName());
                result.put("age", person.getAge());
                result.put("nationality", person.getNationality());
                result.put("gender", person.getGender().toString());
                result.put("found", true);
                log.info("PersonManagementTool#getPersonById found person: {}", person);
                return result;
            } else {
                Map<String, Object> result = new HashMap<>();
                result.put("found", false);
                result.put("message", "Person not found with id: " + id);
                log.info("PersonManagementTool#getPersonById person not found with id: {}", id);
                return result;
            }
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("found", false);
            result.put("error", "Error retrieving person: " + e.getMessage());
            log.error("PersonManagementTool#getPersonById error: ", e);
            return result;
        }
    }

    /**
     * 添加人员
     */
    @Tool(name = "addPerson", description = "Add a new person to the database")
    public Map<String, Object> addPerson(
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

        try {
            Person person = new Person();
            person.setFirstName(firstName);
            person.setLastName(lastName);
            person.setAge(age);
            person.setNationality(nationality);
            person.setGender(Person.Gender.valueOf(gender.toUpperCase()));

            Person savedPerson = personRepository.save(person).block();
            log.info("PersonManagementTool#addPerson saved person: {}", savedPerson);

            if (savedPerson != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("id", savedPerson.getId());
                result.put("firstName", savedPerson.getFirstName());
                result.put("lastName", savedPerson.getLastName());
                result.put("age", savedPerson.getAge());
                result.put("nationality", savedPerson.getNationality());
                result.put("gender", savedPerson.getGender().toString());
                result.put("message", "Person added successfully");
                return result;
            } else {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "Failed to add person");
                return result;
            }
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", "Error adding person: " + e.getMessage());
            log.error("PersonManagementTool#addPerson error: ", e);
            return result;
        }
    }

    /**
     * 删除人员
     */
    @Tool(name = "deletePerson", description = "Delete a person from the database")
    public Map<String, Object> deletePerson(
            @ToolParam(description = "Person's ID") Long id) {
        log.info("PersonManagementTool#deletePerson id: {}", id);

        try {
            // First check if person exists
            Person existingPerson = personRepository.findById(id).block();
            if (existingPerson == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "Person not found with id: " + id);
                log.info("PersonManagementTool#deletePerson person not found with id: {}", id);
                return result;
            }

            // Delete the person
            personRepository.deleteById(id).block();
            log.info("PersonManagementTool#deletePerson deleted person with id: {}", id);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Person deleted successfully");
            result.put("deletedId", id);
            result.put("deletedPerson", Map.of(
                    "firstName", existingPerson.getFirstName(),
                    "lastName", existingPerson.getLastName()
            ));
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", "Error deleting person: " + e.getMessage());
            log.error("PersonManagementTool#deletePerson error: ", e);
            return result;
        }
    }

    /**
     * 获取系统信息
     */
    @Tool(name = "get_system_info", description = "Get system information")
    public Map<String, Object> getSystemInfo() {
        log.info("PersonManagementTool#getSystemInfo");
        return Map.of(
                "server", "mcp-server-v2",
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
                        Map.of("name", "mcp-server-v2", "port", 8061, "status", "active")
                )
        );
    }
} 