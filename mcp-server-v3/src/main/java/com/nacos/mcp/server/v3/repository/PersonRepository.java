package com.nacos.mcp.server.v3.repository;

import com.nacos.mcp.server.v3.model.Person;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PersonRepository extends ReactiveCrudRepository<Person, Long> {

    @Query("SELECT * FROM person WHERE nationality = :nationality")
    Flux<Person> findByNationality(String nationality);

    @Query("SELECT COUNT(*) FROM person WHERE nationality = :nationality")
    Mono<Integer> countByNationality(String nationality);
}
