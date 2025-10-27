package com.nacos.mcp.server.v6.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Person {

    public enum Gender {
        MALE, FEMALE, OTHER
    }

    private Long id;
    private String firstName;
    private String lastName;
    private int age;
    private String nationality;
    private Gender gender;
}
