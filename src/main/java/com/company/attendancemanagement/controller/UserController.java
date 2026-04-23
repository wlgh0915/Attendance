package com.company.attendancemanagement.controller;

import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.dto.user.UserCreateDto;
import com.company.attendancemanagement.service.DepartmentService;
import com.company.attendancemanagement.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.company.attendancemanagement.common.SessionConst.LOGIN_USER;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final DepartmentService departmentService;

    @GetMapping("/users/new")
    public String createForm(HttpSession session, Model model) {
        LoginUserDto loginUser = (LoginUserDto) session.getAttribute(LOGIN_USER);
        if (loginUser == null) {
            return "redirect:/login";
        }

        UserCreateDto dto = new UserCreateDto();
        dto.setCompany(loginUser.getCompany());

        model.addAttribute("userCreateDto", dto);
        model.addAttribute("deptOptions", departmentService.findAllForDropdown(loginUser.getCompany()));
        return "user/create";
    }

    @PostMapping("/users/new")
    public String createUser(@Valid @ModelAttribute("userCreateDto") UserCreateDto dto,
                             BindingResult bindingResult,
                             HttpSession session,
                             Model model,
                             RedirectAttributes redirectAttributes) {

        LoginUserDto loginUser = (LoginUserDto) session.getAttribute(LOGIN_USER);
        if (loginUser == null) {
            return "redirect:/login";
        }

        dto.setCompany(loginUser.getCompany());

        if (bindingResult.hasErrors()) {
            model.addAttribute("deptOptions", departmentService.findAllForDropdown(loginUser.getCompany()));
            return "user/create";
        }

        boolean result = userService.createUser(dto);

        if (!result) {
            bindingResult.reject("duplicate", "이미 존재하는 사번입니다.");
            model.addAttribute("deptOptions", departmentService.findAllForDropdown(loginUser.getCompany()));
            return "user/create";
        }

        redirectAttributes.addFlashAttribute("successMessage", "사원 등록에 성공했습니다.");
        return "redirect:/";
    }
}