package com.company.attendancemanagement.controller;

import com.company.attendancemanagement.common.SessionConst;
import com.company.attendancemanagement.dto.login.LoginRequestDto;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.service.LoginService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;

    @GetMapping("/login")
    public String loginForm(Model model) {
        model.addAttribute("loginRequestDto", new LoginRequestDto());
        return "login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("loginRequestDto") LoginRequestDto loginRequestDto,
                        BindingResult bindingResult,
                        HttpSession session) {

        if (bindingResult.hasErrors()) {
            return "login";
        }

        LoginUserDto loginUser = loginService.login(loginRequestDto);

        if (loginUser == null) {
            bindingResult.reject("loginFail", "로그인 정보가 올바르지 않습니다.");
            return "login";
        }

        session.setAttribute(SessionConst.LOGIN_USER, loginUser);
        return "redirect:/";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}