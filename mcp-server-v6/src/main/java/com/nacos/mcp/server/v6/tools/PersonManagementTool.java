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
    
    private static Person createPerson(long id, String firstName, String lastName, int age,
                                       String nationality, Person.Gender gender) {
        Person person = new Person();
        person.setId(id);
        person.setFirstName(firstName);
        person.setLastName(lastName);
        person.setAge(age);
        person.setNationality(nationality);
        person.setGender(gender);
        return person;
    }

    static {
        MOCK_USER.add(createPerson(1L, "John", "Doe", 30, "American", Person.Gender.MALE));
        MOCK_USER.add(createPerson(2L, "Jane", "Smith", 25, "British", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(3L, "Hans", "Mueller", 35, "German", Person.Gender.MALE));
        MOCK_USER.add(createPerson(4L, "Maria", "Schmidt", 28, "German", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(5L, "Pierre", "Dubois", 40, "French", Person.Gender.MALE));
        MOCK_USER.add(createPerson(6L, "Sophie", "Martin", 32, "French", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(7L, "Akira", "Tanaka", 29, "Japanese", Person.Gender.MALE));
        MOCK_USER.add(createPerson(8L, "Yuki", "Sato", 26, "Japanese", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(9L, "Marco", "Rossi", 33, "Italian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(10L, "Elena", "Garcia", 27, "Spanish", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(11L, "Tomas", "Hernandez", 31, "Spanish", Person.Gender.MALE));
        MOCK_USER.add(createPerson(12L, "Li", "Wang", 24, "Chinese", Person.Gender.MALE));
        MOCK_USER.add(createPerson(13L, "Zhang", "Li", 28, "Chinese", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(14L, "Carlos", "Silva", 34, "Brazilian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(15L, "Ana", "Costa", 27, "Brazilian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(16L, "Olga", "Ivanova", 36, "Russian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(17L, "Sergei", "Petrov", 38, "Russian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(18L, "Amir", "Khan", 33, "Indian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(19L, "Priya", "Singh", 29, "Indian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(20L, "Noah", "Johnson", 31, "Canadian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(21L, "Emma", "Wilson", 26, "Canadian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(22L, "Lars", "Andersen", 37, "Danish", Person.Gender.MALE));
        MOCK_USER.add(createPerson(23L, "Freja", "Nielsen", 30, "Danish", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(24L, "Mateo", "Lopez", 35, "Mexican", Person.Gender.MALE));
        MOCK_USER.add(createPerson(25L, "Camila", "Lopez", 33, "Mexican", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(26L, "Omar", "Mahmoud", 34, "Egyptian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(27L, "Layla", "Hassan", 27, "Egyptian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(28L, "Musa", "Abiola", 32, "Nigerian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(29L, "Ada", "Okafor", 29, "Nigerian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(30L, "Ethan", "Brown", 34, "Australian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(31L, "Olivia", "Taylor", 27, "Australian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(32L, "Lucas", "Miller", 33, "American", Person.Gender.MALE));
        MOCK_USER.add(createPerson(33L, "Isabella", "Anderson", 25, "American", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(34L, "Hugo", "Lambert", 36, "French", Person.Gender.MALE));
        MOCK_USER.add(createPerson(35L, "Claire", "Bernard", 29, "French", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(36L, "Rafael", "Fernandez", 34, "Argentinian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(37L, "Valentina", "Perez", 28, "Argentinian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(38L, "Jonas", "Kristensen", 31, "Norwegian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(39L, "Ingrid", "Larsen", 27, "Norwegian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(40L, "Sebastian", "Weber", 32, "German", Person.Gender.MALE));
        MOCK_USER.add(createPerson(41L, "Greta", "Fischer", 30, "German", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(42L, "Mateusz", "Kowalski", 35, "Polish", Person.Gender.MALE));
        MOCK_USER.add(createPerson(43L, "Agnieszka", "Nowak", 28, "Polish", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(44L, "Tariq", "Hussain", 33, "Pakistani", Person.Gender.MALE));
        MOCK_USER.add(createPerson(45L, "Sana", "Qureshi", 26, "Pakistani", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(46L, "Nikos", "Papadopoulos", 37, "Greek", Person.Gender.MALE));
        MOCK_USER.add(createPerson(47L, "Eleni", "Katsaros", 31, "Greek", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(48L, "Lucas", "Silveira", 29, "Portuguese", Person.Gender.MALE));
        MOCK_USER.add(createPerson(49L, "Beatriz", "Morais", 27, "Portuguese", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(50L, "Andres", "Gomez", 34, "Colombian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(51L, "Sofia", "Gomez", 32, "Colombian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(52L, "Liam", "O'Connor", 33, "Irish", Person.Gender.MALE));
        MOCK_USER.add(createPerson(53L, "Aoife", "Murphy", 26, "Irish", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(54L, "Yusuf", "Ali", 31, "Kenyan", Person.Gender.MALE));
        MOCK_USER.add(createPerson(55L, "Amina", "Njeri", 29, "Kenyan", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(56L, "Andrei", "Popescu", 35, "Romanian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(57L, "Ioana", "Marin", 28, "Romanian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(58L, "Mikhail", "Sidorov", 34, "Russian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(59L, "Daria", "Volkova", 27, "Russian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(60L, "Chen", "Yao", 29, "Chinese", Person.Gender.MALE));
        MOCK_USER.add(createPerson(61L, "Mei", "Zhou", 26, "Chinese", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(62L, "Min-Jun", "Park", 32, "South Korean", Person.Gender.MALE));
        MOCK_USER.add(createPerson(63L, "Seo-yeon", "Kim", 28, "South Korean", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(64L, "Farid", "Aziz", 33, "Indonesian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(65L, "Putri", "Rahma", 25, "Indonesian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(66L, "Mohammed", "Al Saud", 36, "Saudi Arabian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(67L, "Aisha", "Al Rashid", 29, "Saudi Arabian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(68L, "David", "Levy", 34, "Israeli", Person.Gender.MALE));
        MOCK_USER.add(createPerson(69L, "Yael", "Cohen", 27, "Israeli", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(70L, "Ian", "Campbell", 35, "Scottish", Person.Gender.MALE));
        MOCK_USER.add(createPerson(71L, "Fiona", "MacDonald", 30, "Scottish", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(72L, "Leon", "van Dijk", 33, "Dutch", Person.Gender.MALE));
        MOCK_USER.add(createPerson(73L, "Eva", "Bakker", 27, "Dutch", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(74L, "Filip", "Novak", 32, "Czech", Person.Gender.MALE));
        MOCK_USER.add(createPerson(75L, "Tereza", "Svoboda", 28, "Czech", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(76L, "Javier", "Torres", 37, "Chilean", Person.Gender.MALE));
        MOCK_USER.add(createPerson(77L, "Gabriela", "Torres", 31, "Chilean", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(78L, "Ben", "Williams", 29, "New Zealander", Person.Gender.MALE));
        MOCK_USER.add(createPerson(79L, "Mia", "Thompson", 26, "New Zealander", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(80L, "Kwame", "Mensah", 34, "Ghanaian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(81L, "Akosua", "Boateng", 28, "Ghanaian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(82L, "Samir", "Darwish", 33, "Jordanian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(83L, "Maya", "Farah", 27, "Jordanian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(84L, "Zane", "Cooper", 31, "South African", Person.Gender.MALE));
        MOCK_USER.add(createPerson(85L, "Naledi", "Dlamini", 29, "South African", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(86L, "Victor", "Hernandez", 32, "Venezuelan", Person.Gender.MALE));
        MOCK_USER.add(createPerson(87L, "Isabel", "Rivas", 27, "Venezuelan", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(88L, "Juan", "Paredes", 34, "Peruvian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(89L, "Lucia", "Rojas", 28, "Peruvian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(90L, "Arman", "Hakobyan", 35, "Armenian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(91L, "Nare", "Petrosyan", 30, "Armenian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(92L, "Viktor", "Horvath", 33, "Hungarian", Person.Gender.MALE));
        MOCK_USER.add(createPerson(93L, "Reka", "Farkas", 27, "Hungarian", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(94L, "Anders", "Johansson", 36, "Swedish", Person.Gender.MALE));
        MOCK_USER.add(createPerson(95L, "Astrid", "Lindberg", 29, "Swedish", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(96L, "Jakub", "Horak", 34, "Slovak", Person.Gender.MALE));
        MOCK_USER.add(createPerson(97L, "Zuzana", "Bielik", 28, "Slovak", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(98L, "Mustafa", "Bekir", 33, "Turkish", Person.Gender.MALE));
        MOCK_USER.add(createPerson(99L, "Selin", "Demir", 26, "Turkish", Person.Gender.FEMALE));
        MOCK_USER.add(createPerson(100L, "Alex", "Novak", 31, "Croatian", Person.Gender.MALE));
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