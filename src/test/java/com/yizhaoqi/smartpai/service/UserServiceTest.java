package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.OrganizationTagRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.utils.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationTagRepository organizationTagRepository;

    @Mock
    private OrgTagCacheService orgTagCacheService;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testRegisterUser_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(organizationTagRepository.existsByTagId("DEFAULT")).thenReturn(true);
        when(organizationTagRepository.existsByTagId("PRIVATE_testuser")).thenReturn(false);

        userService.registerUser("testuser", "password123");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(userCaptor.capture());

        User savedUser = userCaptor.getAllValues().get(1);
        assertNotNull(savedUser);
        assertEquals("testuser", savedUser.getUsername());
        assertNotEquals("password123", savedUser.getPassword());
        assertTrue(PasswordUtil.matches("password123", savedUser.getPassword()));
        assertEquals(User.Role.USER, savedUser.getRole());
        assertEquals("PRIVATE_testuser", savedUser.getOrgTags());
        assertEquals("PRIVATE_testuser", savedUser.getPrimaryOrg());

        verify(organizationTagRepository).save(argThat(tag ->
                "PRIVATE_testuser".equals(tag.getTagId()) && tag.getCreatedBy() == savedUser));
        verify(orgTagCacheService).cacheUserOrgTags("testuser", List.of("PRIVATE_testuser"));
        verify(orgTagCacheService).cacheUserPrimaryOrg("testuser", "PRIVATE_testuser");
    }

    @Test
    void testRegisterUser_UsernameExists() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(new User()));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> userService.registerUser("testuser", "password123"));

        assertEquals("Username already exists", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void testAuthenticateUser_Success() {
        String rawPassword = "password123";
        User user = new User();
        user.setUsername("testuser");
        user.setPassword(PasswordUtil.encode(rawPassword));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        String username = userService.authenticateUser("testuser", rawPassword);

        assertEquals("testuser", username);
    }

    @Test
    void testAuthenticateUser_InvalidCredentials() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> userService.authenticateUser("testuser", "wrongpassword"));

        assertEquals("Invalid username or password", exception.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }
}
