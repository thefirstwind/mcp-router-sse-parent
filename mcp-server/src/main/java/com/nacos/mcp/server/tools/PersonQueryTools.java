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
public class PersonQueryTools {

    private final PersonRepository personRepository;

    /**
     * Find a person by their ID
     */
    @Tool(description = "Find a person by their ID number")
    public Person getPersonById(@ToolParam(description = "userId", required = true) Long id) {
        return personRepository.findById(id).orElse(null);
    }

    /**
     * Find all persons by their nationality
     */
    @Tool(description = "Find all persons with a specific nationality")
    public List<Person> getPersonsByNationality(@ToolParam(description = "nationality", required = true) String nationality) {
        return personRepository.findByNationality(nationality);
    }

    /**
     * Get all persons in the database
     */
    @Tool(description = "Get all persons in the database")
    public List<Person> getAllPersons() {
        return (List<Person>) personRepository.findAll();
    }

    /**
     * Count persons by nationality
     */
    @Tool(description = "Count how many persons have a specific nationality")
    public long countPersonsByNationality(@ToolParam(description = "nationality", required = true) String nationality) {
        return personRepository.findByNationality(nationality).size();
    }

}