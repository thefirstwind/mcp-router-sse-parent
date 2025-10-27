package com.nacos.mcp.server.v6.tools;

import com.nacos.mcp.server.v6.model.Person;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Person管理工具类
 * 使用Spring AI的@Tool注解实现MCP工具
 */
@Slf4j
@Service
public class PersonManagementTool {


    private static final List<Person> MOCK_USER = new ArrayList<>();

    static {
        Person person1 = new Person();
        person1.setId(1L);
        person1.setFirstName("John");
        person1.setLastName("Doe");
        person1.setAge(30);
        person1.setNationality("American");
        person1.setGender(Person.Gender.MALE);
        MOCK_USER.add(person1);

        Person person2 = new Person();
        person2.setId(2L);
        person2.setFirstName("Jane");
        person2.setLastName("Smith");
        person2.setAge(25);
        person2.setNationality("British");
        person2.setGender(Person.Gender.FEMALE);
        MOCK_USER.add(person2);

        Person person3 = new Person();
        person3.setId(3L);
        person3.setFirstName("Hans");
        person3.setLastName("Mueller");
        person3.setAge(35);
        person3.setNationality("German");
        person3.setGender(Person.Gender.MALE);
        MOCK_USER.add(person3);

        Person person4 = new Person();
        person4.setId(4L);
        person4.setFirstName("Maria");
        person4.setLastName("Schmidt");
        person4.setAge(28);
        person4.setNationality("German");
        person4.setGender(Person.Gender.FEMALE);
        MOCK_USER.add(person4);

        Person person5 = new Person();
        person5.setId(5L);
        person5.setFirstName("Pierre");
        person5.setLastName("Dubois");
        person5.setAge(40);
        person5.setNationality("French");
        person5.setGender(Person.Gender.MALE);
        MOCK_USER.add(person5);

        Person person6 = new Person();
        person6.setId(6L);
        person6.setFirstName("Sophie");
        person6.setLastName("Martin");
        person6.setAge(32);
        person6.setNationality("French");
        person6.setGender(Person.Gender.FEMALE);
        MOCK_USER.add(person6);

        Person person7 = new Person();
        person7.setId(7L);
        person7.setFirstName("Akira");
        person7.setLastName("Tanaka");
        person7.setAge(29);
        person7.setNationality("Japanese");
        person7.setGender(Person.Gender.MALE);
        MOCK_USER.add(person7);

        Person person8 = new Person();
        person8.setId(8L);
        person8.setFirstName("Yuki");
        person8.setLastName("Sato");
        person8.setAge(26);
        person8.setNationality("Japanese");
        person8.setGender(Person.Gender.FEMALE);
        MOCK_USER.add(person8);

        Person person9 = new Person();
        person9.setId(9L);
        person9.setFirstName("Marco");
        person9.setLastName("Rossi");
        person9.setAge(33);
        person9.setNationality("Italian");
        person9.setGender(Person.Gender.MALE);
        MOCK_USER.add(person9);

        Person person10 = new Person();
        person10.setId(10L);
        person10.setFirstName("Elena");
        person10.setLastName("Garcia");
        person10.setAge(27);
        person10.setNationality("Spanish");
        person10.setGender(Person.Gender.FEMALE);
        MOCK_USER.add(person10);

        Person person11 = new Person();
        person11.setId(11L);
        person11.setFirstName("Tomas");
        person11.setLastName("Hernandez");
        person11.setAge(31);
        person11.setNationality("Spanish");
        person11.setGender(Person.Gender.MALE);
        MOCK_USER.add(person11);

        Person person12 = new Person();
        person12.setId(12L);
        person12.setFirstName("Li");
        person12.setLastName("Wang");
        person12.setAge(24);
        person12.setNationality("Chinese");
        person12.setGender(Person.Gender.MALE);
        MOCK_USER.add(person12);

        Person person13 = new Person();
        person13.setId(13L);
        person13.setFirstName("Zhang");
        person13.setLastName("Li");
        person13.setAge(28);
        person13.setNationality("Chinese");
        person13.setGender(Person.Gender.FEMALE);
        MOCK_USER.add(person13);
    }

    /**
     * 获取所有人员
     */
    @Tool(name = "getAllPersons", description = "Get all persons from the database")
    public List<Map<String, Object>> getAllPersons() {
        log.info("PersonManagementTool#getAllPersons");
        List<Person> persons = MOCK_USER;
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
            Optional<Person> personOpt = MOCK_USER.stream().filter(p -> p.getId() == id).findAny();
            if (personOpt.isPresent()) {
                Person person = personOpt.get();
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

            person.setId(System.currentTimeMillis()); // 使用时间戳作为临时ID
            MOCK_USER.add(person);
            log.info("PersonManagementTool#addPerson saved person: {}", person);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("id", person.getId());
            result.put("firstName", person.getFirstName());
            result.put("lastName", person.getLastName());
            result.put("age", person.getAge());
            result.put("nationality", person.getNationality());
            result.put("gender", person.getGender().toString());
            result.put("message", "Person added successfully");
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", "Error adding person: " + e.getMessage());
            log.error("PersonManagementTool#addPerson error: ", e);
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