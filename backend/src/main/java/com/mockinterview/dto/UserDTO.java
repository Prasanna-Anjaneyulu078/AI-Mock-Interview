package com.mockinterview.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String fullName;
    private String email;
    private String role;
    private String profileImage;
    // Map to client expectation:
    // client uses "name" and "picture", we can either add getters/setters or map them in MapStruct
    
    public String getName() {
        return this.fullName;
    }
    public String getPicture() {
        return this.profileImage;
    }
}
