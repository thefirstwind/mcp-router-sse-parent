package com.nacos.mcp.router.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Search Request Model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

    /**
     * Task description
     */
    @NotBlank(message = "Task description cannot be blank")
    private String taskDescription;

    /**
     * Keywords
     */
    private List<String> keywords;

    /**
     * Minimum similarity score
     */
    private Double minSimilarity;

    /**
     * Maximum number of results
     */
    private Integer limit;
} 