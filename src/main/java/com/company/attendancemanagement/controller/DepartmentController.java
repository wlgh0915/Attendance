package com.company.attendancemanagement.controller;

import com.company.attendancemanagement.dto.department.DepartmentCreateDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.service.DepartmentService;
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
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping("/departments")
    public String list(HttpSession session, Model model) {
        LoginUserDto loginUser = (LoginUserDto) session.getAttribute(LOGIN_USER);
        if (loginUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("departments", departmentService.findAll(loginUser.getCompany()));
        return "department/list";
    }

    @GetMapping("/departments/new")
    public String createForm(HttpSession session, Model model) {
        LoginUserDto loginUser = (LoginUserDto) session.getAttribute(LOGIN_USER);
        if (loginUser == null) {
            return "redirect:/login";
        }

        DepartmentCreateDto dto = new DepartmentCreateDto();
        dto.setCompany(loginUser.getCompany());

        model.addAttribute("departmentCreateDto", dto);
        return "department/create";
    }

    @PostMapping("/departments/new")
    public String create(@Valid @ModelAttribute("departmentCreateDto") DepartmentCreateDto dto,
                         BindingResult bindingResult,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {

        LoginUserDto loginUser = (LoginUserDto) session.getAttribute(LOGIN_USER);
        if (loginUser == null) {
            return "redirect:/login";
        }

        dto.setCompany(loginUser.getCompany());

        if (bindingResult.hasErrors()) {
            return "department/create";
        }

        boolean result = departmentService.createDepartment(dto);

        if (!result) {
            bindingResult.reject("duplicate", "이미 존재하는 부서코드입니다.");
            return "department/create";
        }

        redirectAttributes.addFlashAttribute("successMessage", "부서 등록에 성공했습니다.");
        return "redirect:/departments";
    }

    @GetMapping("/departments/employees")
    public String employees(String company,
                            String deptCode,
                            HttpSession session,
                            Model model) {

        LoginUserDto loginUser = (LoginUserDto) session.getAttribute(LOGIN_USER);
        if (loginUser == null) {
            return "redirect:/login";
        }

        DepartmentDto dept = departmentService.findByDeptCode(company, deptCode);

        model.addAttribute("company", company);
        model.addAttribute("deptCode", deptCode);
        model.addAttribute("deptName", dept.getDeptName());
        model.addAttribute("employees",
                departmentService.findEmployeesByDept(company, deptCode));

        return "department/employees";
    }
}