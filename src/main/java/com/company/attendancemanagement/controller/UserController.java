package com.company.attendancemanagement.controller;

import com.company.attendancemanagement.dto.user.UserCreateDto;
import com.company.attendancemanagement.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/users/new")
    public String createForm(Model model) {
        model.addAttribute("userCreateDto", new UserCreateDto());
        return "user/create";
    }

    @PostMapping("/users/new")
    public String createUser(@Valid @ModelAttribute("userCreateDto") UserCreateDto dto,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "user/create";
        }

        boolean result = userService.createUser(dto);

        if (!result) {
            bindingResult.reject("duplicate", "이미 존재하는 사번입니다.");
            return "user/create";
        }

        redirectAttributes.addFlashAttribute("successMessage", "사원 등록에 성공했습니다.");
        return "redirect:/";
    }
}