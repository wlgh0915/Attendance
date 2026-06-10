package com.company.attendancemanagement.controller;

import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.dto.user.UserCreateDto;
import com.company.attendancemanagement.dto.user.UserListDto;
import com.company.attendancemanagement.dto.user.UserUpdateDto;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.company.attendancemanagement.common.SessionConst.LOGIN_USER;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final DepartmentService departmentService;

    @GetMapping("/users")
    public String listUsers(HttpSession session, Model model) {
        LoginUserDto loginUser = (LoginUserDto) session.getAttribute(LOGIN_USER);
        if (loginUser == null) return "redirect:/login";

        String company = loginUser.getCompany();
        model.addAttribute("users", userService.findAllUsers(company));
        model.addAttribute("deptOptions", departmentService.findAllForDropdown(company));
        return "user/list";
    }

    @GetMapping("/users/new")
    public String createForm(HttpSession session, Model model) {
        LoginUserDto loginUser = (LoginUserDto) session.getAttribute(LOGIN_USER);
        if (loginUser == null) {
            return "redirect:/login";
        }

        UserCreateDto dto = new UserCreateDto();
        dto.setCompany(loginUser.getCompany());
        dto.setEmpCode(userService.generateNextEmpCode(loginUser.getCompany()));

        model.addAttribute("userCreateDto", dto);
        addUserFormOptions(model, loginUser.getCompany());
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
            dto.setEmpCode(userService.generateNextEmpCode(loginUser.getCompany()));
            addUserFormOptions(model, loginUser.getCompany());
            return "user/create";
        }

        boolean result = userService.createUser(dto);

        if (!result) {
            dto.setEmpCode(userService.generateNextEmpCode(loginUser.getCompany()));
            bindingResult.reject("duplicate", "이미 존재하는 사번입니다.");
            addUserFormOptions(model, loginUser.getCompany());
            return "user/create";
        }

        redirectAttributes.addFlashAttribute("successMessage", "사원 등록에 성공했습니다.");
        return "redirect:/";
    }

    @GetMapping("/users/{empCode}/edit")
    public String editForm(@PathVariable String empCode,
                           @RequestParam(value = "returnDeptCode", required = false) String returnDeptCode,
                           HttpSession session,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        LoginUserDto loginUser = (LoginUserDto) session.getAttribute(LOGIN_USER);
        if (loginUser == null) {
            return "redirect:/login";
        }

        UserUpdateDto dto = userService.findUserForEdit(loginUser.getCompany(), empCode);
        if (dto == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "존재하지 않는 사원입니다.");
            return redirectToEmployeeList(returnDeptCode);
        }

        dto.setReturnDeptCode(returnDeptCode);
        model.addAttribute("userUpdateDto", dto);
        addUserFormOptions(model, loginUser.getCompany());
        return "user/edit";
    }

    @PostMapping("/users/{empCode}/edit")
    public String editUser(@PathVariable String empCode,
                           @Valid @ModelAttribute("userUpdateDto") UserUpdateDto dto,
                           BindingResult bindingResult,
                           HttpSession session,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        LoginUserDto loginUser = (LoginUserDto) session.getAttribute(LOGIN_USER);
        if (loginUser == null) {
            return "redirect:/login";
        }

        dto.setCompany(loginUser.getCompany());
        dto.setEmpCode(empCode);

        if (bindingResult.hasErrors()) {
            addUserFormOptions(model, loginUser.getCompany());
            return "user/edit";
        }

        boolean result;
        try {
            result = userService.updateUser(dto);
        } catch (IllegalArgumentException e) {
            bindingResult.reject("invalidRole", e.getMessage());
            addUserFormOptions(model, loginUser.getCompany());
            return "user/edit";
        }
        if (!result) {
            bindingResult.reject("updateFailed", "사원 수정에 실패했습니다.");
            addUserFormOptions(model, loginUser.getCompany());
            return "user/edit";
        }

        redirectAttributes.addFlashAttribute("successMessage", "사원 수정에 성공했습니다.");
        return redirectToEmployeeList(dto.getReturnDeptCode());
    }

    private void addUserFormOptions(Model model, String company) {
        model.addAttribute("deptOptions", departmentService.findAllForDropdown(company));
        model.addAttribute("positionOptions", userService.findActivePositions(company));
        model.addAttribute("dutyOptions", userService.findActiveDuties(company));
        model.addAttribute("roleOptions", userService.findActiveRoles(company));
    }

    private String redirectToEmployeeList(String deptCode) {
        if (deptCode == null || deptCode.isBlank()) {
            return "redirect:/users";
        }
        return "redirect:/departments/employees?deptCode=" + deptCode;
    }
}
