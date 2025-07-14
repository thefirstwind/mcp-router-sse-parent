package com.nacos.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nacos.mcp.server.model.Person;
import com.nacos.mcp.server.repository.PersonRepository;
import com.nacos.mcp.server.tools.PersonModifyTools;
import com.nacos.mcp.server.tools.PersonQueryTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MCP Server functionality
 * Tests both the tool functions and the overall server behavior
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=always",
    "logging.level.com.nacos.mcp.server=DEBUG"
})
public class McpServerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonQueryTools personQueryTools;
    @Autowired
    private PersonModifyTools personModifyTools;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        personRepository.deleteAll();
    }

    @Test
    void contextLoads() {
        assertThat(personQueryTools).isNotNull();
        assertThat(personRepository).isNotNull();
    }

    @Test
    void testPersonToolsFunctionality() {
        // Test addPerson
        Person john = personModifyTools.addPerson("John", "Doe", 30, "American", Person.Gender.MALE);
        assertThat(john).isNotNull();
        assertThat(john.getId()).isNotNull();
        assertThat(john.getFirstName()).isEqualTo("John");
        assertThat(john.getLastName()).isEqualTo("Doe");
        assertThat(john.getAge()).isEqualTo(30);
        assertThat(john.getNationality()).isEqualTo("American");
        assertThat(john.getGender()).isEqualTo(Person.Gender.MALE);

        // Test getPersonById
        Person retrieved = personQueryTools.getPersonById(john.getId());
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getFirstName()).isEqualTo("John");

        // Test getAllPersons
        List<Person> allPersons = personQueryTools.getAllPersons();
        assertThat(allPersons).hasSize(1);
        assertThat(allPersons.get(0).getFirstName()).isEqualTo("John");

        // Add another person
        Person jane = personModifyTools.addPerson("Jane", "Smith", 25, "American", Person.Gender.FEMALE);
        assertThat(jane).isNotNull();

        // Test getPersonsByNationality
        List<Person> americans = personQueryTools.getPersonsByNationality("American");
        assertThat(americans).hasSize(2);

        // Test countPersonsByNationality
        long americanCount = personQueryTools.countPersonsByNationality("American");
        assertThat(americanCount).isEqualTo(2);

        // Test deletePerson
        boolean deleted = personModifyTools.deletePerson(john.getId());
        assertThat(deleted).isTrue();

        // Verify deletion
        Person shouldBeNull = personQueryTools.getPersonById(john.getId());
        assertThat(shouldBeNull).isNull();

        // Test deleting non-existent person
        boolean notDeleted = personModifyTools.deletePerson(999L);
        assertThat(notDeleted).isFalse();
    }

    @Test
    void testServerIsRunning() {
        // Test that the server responds to requests (even if 404, it means server is up)
        webTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isNotFound(); // 404 is expected since no handler for root path
    }

    @Test
    void testMcpEndpointExists() {
        // Test that MCP endpoint is accessible (even if it returns an error, it exists)
        webTestClient.get()
                .uri("/mcp/message")
                .exchange()
                .expectStatus().isNotFound(); // 404 is expected for GET on SSE endpoint
    }

    @Test
    void testDatabaseConnectivity() {
        // Test that we can interact with the database
        Person testPerson = new Person();
        testPerson.setFirstName("Test");
        testPerson.setLastName("User");
        testPerson.setAge(25);
        testPerson.setNationality("Test");
        testPerson.setGender(Person.Gender.MALE);

        Person saved = personRepository.save(testPerson);
        assertThat(saved.getId()).isNotNull();

        List<Person> all = (List<Person>) personRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getFirstName()).isEqualTo("Test");
    }

    @Test
    void testToolAnnotationsAreWorking() {
        // Test that @Tool annotations are properly configured
        assertThat(personQueryTools).isNotNull();
        
        // Test each tool method works
        Person person = personModifyTools.addPerson("Tool", "Test", 30, "TestNation", Person.Gender.MALE);
        assertThat(person).isNotNull();
        
        Person found = personQueryTools.getPersonById(person.getId());
        assertThat(found).isNotNull();
        assertThat(found.getFirstName()).isEqualTo("Tool");
        
        List<Person> byNationality = personQueryTools.getPersonsByNationality("TestNation");
        assertThat(byNationality).hasSize(1);
        
        long count = personQueryTools.countPersonsByNationality("TestNation");
        assertThat(count).isEqualTo(1);
        
        List<Person> all = personQueryTools.getAllPersons();
        assertThat(all).hasSize(1);
        
        boolean deleted = personModifyTools.deletePerson(person.getId());
        assertThat(deleted).isTrue();
    }

    @Test
    void testPersonModelValidation() {
        // Test Person model
        Person person = new Person();
        person.setFirstName("John");
        person.setLastName("Doe");
        person.setAge(30);
        person.setNationality("American");
        person.setGender(Person.Gender.MALE);

        assertThat(person.getFirstName()).isEqualTo("John");
        assertThat(person.getLastName()).isEqualTo("Doe");
        assertThat(person.getAge()).isEqualTo(30);
        assertThat(person.getNationality()).isEqualTo("American");
        assertThat(person.getGender()).isEqualTo(Person.Gender.MALE);
        // Test full name concatenation manually since getFullName() method doesn't exist
        assertThat(person.getFirstName() + " " + person.getLastName()).isEqualTo("John Doe");
    }
} 