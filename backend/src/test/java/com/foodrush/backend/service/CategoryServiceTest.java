package com.foodrush.backend.service;

import com.foodrush.backend.dto.CategoryDTO;
import com.foodrush.backend.entity.Category;
import com.foodrush.backend.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository);
    }

    @Test
    void getAllCategories_returnsAllCategoriesMappedToDTOs() {
        Category northIndian = Category.builder().id(1L).name("North Indian").build();
        Category desserts = Category.builder().id(2L).name("Desserts").build();
        when(categoryRepository.findAll()).thenReturn(List.of(northIndian, desserts));

        List<CategoryDTO> result = categoryService.getAllCategories();

        assertThat(result).containsExactly(
                new CategoryDTO(1L, "North Indian"),
                new CategoryDTO(2L, "Desserts"));
    }
}
