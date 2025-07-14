package com.nacos.mcp.server.tools;

import com.nacos.mcp.server.model.Person;
import com.nacos.mcp.server.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Person management tools for MCP Server
 * These methods provide person management functionality via MCP protocol
 * Methods are automatically discovered by MethodToolCallbackProvider
 */
@Service
@RequiredArgsConstructor
public class PersonModifyTools {

    private final PersonRepository personRepository;

    /**
     * Add a new person to the database
     */
    @Tool(description = "Add a new person to the database with their personal information")
    public Person addPerson(String firstName, String lastName, int age, String nationality, Person.Gender gender) {
        Person newPerson = new Person();
        newPerson.setFirstName(firstName);
        newPerson.setLastName(lastName);
        newPerson.setAge(age);
        newPerson.setNationality(nationality);
        newPerson.setGender(gender);
        return personRepository.save(newPerson);
    }

    /**
     * Delete a person by ID
     */
    @Tool(description = "Delete a person from the database using their ID number")
    public boolean deletePerson(@ToolParam(description = "userId", required = true)Long id) {
        if (personRepository.existsById(id)) {
            personRepository.deleteById(id);
            return true;
        }
        return false;
    }
}