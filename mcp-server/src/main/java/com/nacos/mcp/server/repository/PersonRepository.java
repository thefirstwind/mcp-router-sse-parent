package com.nacos.mcp.server.repository;

import com.nacos.mcp.server.model.Person;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PersonRepository extends CrudRepository<Person, Long> {
    List<Person> findByNationality(String nationality);
} 