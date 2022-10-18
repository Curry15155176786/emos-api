package com.example.emos.api.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
@Schema(description = "修改密码表单")
public class UpdatePasswordForm {
            @NotBlank(message = "password不能为空")
            @Pattern(regexp = "^[a-zA-Z0-9]{6,20}$",message = "password内容不正确")
            @Schema(description = "密码")
            String password;
            @NotBlank(message = "新密码不能为空")
            @Pattern(regexp = "^[a-zA-Z0-9]{6,20}$",message = "newPassword内容不正确")
            @Schema(description = "密码")
            private String newPassword;

}
