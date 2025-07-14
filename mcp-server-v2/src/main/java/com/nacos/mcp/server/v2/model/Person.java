package com.nacos.mcp.server.v2.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("person")
public class Person {

    public enum Gender {
        MALE, FEMALE, OTHER
    }

    @Id
    private Long id;
    private String firstName;
    private String lastName;
    private int age;
    private String nationality;
    private Gender gender;
}
