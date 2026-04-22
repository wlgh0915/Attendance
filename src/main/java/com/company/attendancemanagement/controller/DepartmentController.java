package com.company.attendancemanagement.controller;

import com.company.attendancemanagement.dto.department.DepartmentCreateDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.department.DepartmentUpdateDto;
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
        model.addAttribute("deptOptions", departmentService.findAllForDropdown(loginUser.getCompany()));
        model.addAttribute("employeeOptions", departmentService.findActiveEmployees(loginUser.getCompany()));
        return "department/create";
    }

    @PostMapping("/departments/new")
    public String create(@Valid @ModelAttribute("departmentCreateDto") DepartmentCreateDto dto,
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
            model.addAttribute("employeeOptions", departmentService.findActiveEmployees(loginUser.getCompany()));
            return "department/create";
        }

        boolean result = departmentService.createDepartment(dto);

        if (!result) {
            bindingResult.reject("duplicate", "이미 존재하는 부서코드입니다.");
            model.addAttribute("deptOptions", departmentService.findAllForDropdown(loginUser.getCompany()));
            model.addAttribute("employeeOptions", departmentService.findActiveEmployees(loginUser.getCompany()));
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

        model.addAttribute("companyName", dept.getCompanyName());
        model.addAttribute("deptCode", deptCode);
        model.addAttribute("deptName", dept.getDeptName());
        model.addAttribute("employees",
                departmentService.findEmployeesByDept(company, deptCode));

        return "department/employees";
    }

    @GetMapping("/departments/edit")
    public String editList(HttpSession session, Model model) {
        LoginUserDto loginUser = (LoginUserDto) session.getAttribute(LOGIN_USER);
        if (loginUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("departments", departmentService.findAll(loginUser.getCompany()));
        return "department/edit-list";
    }

    @GetMapping(value = "/departments/edit", params = {"company", "deptCode"})
    public String editForm(String company,
                           String deptCode,
                           HttpSession session,
                           Model model) {
        LoginUserDto loginUser = (LoginUserDto) session.getAttribute(LOGIN_USER);
        if (loginUser == null) {
            return "redirect:/login";
        }

        DepartmentDto dept = departmentService.findByDeptCode(company, deptCode);
        if (dept == null) {
            return "redirect:/departments/edit";
        }

        DepartmentUpdateDto dto = new DepartmentUpdateDto();
        dto.setCompany(dept.getCompany());
        dto.setDeptCode(dept.getDeptCode());
        dto.setDeptName(dept.getDeptName());
        dto.setParentDept(dept.getParentDept());
        dto.setDeptLeader(dept.getDeptLeader());
        dto.setDeptCategory(dept.getDeptCategory());
        dto.setWorkPatternCode(dept.getWorkPatternCode());
        dto.setUseYn(dept.getUseYn());
        dto.setStartDate(dept.getStartDate());
        dto.setEndDate(dept.getEndDate());

        model.addAttribute("departmentUpdateDto", dto);
        model.addAttribute("deptOptions", departmentService.findAllForDropdown(loginUser.getCompany()));
        model.addAttribute("employeeOptions", departmentService.findActiveEmployees(loginUser.getCompany()));
        return "department/edit";
    }

    @PostMapping("/departments/edit")
    public String edit(@Valid @ModelAttribute("departmentUpdateDto") DepartmentUpdateDto dto,
                       BindingResult bindingResult,
                       HttpSession session,
                       Model model,
                       RedirectAttributes redirectAttributes) {

        LoginUserDto loginUser = (LoginUserDto) session.getAttribute(LOGIN_USER);
        if (loginUser == null) {
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("deptOptions", departmentService.findAllForDropdown(loginUser.getCompany()));
            model.addAttribute("employeeOptions", departmentService.findActiveEmployees(loginUser.getCompany()));
            return "department/edit";
        }

        boolean result = departmentService.updateDepartment(dto);

        if (!result) {
            bindingResult.reject("updateFailed", "부서에 소속된 직원이 있으면 사용 여부를 N으로 변경할 수 없습니다.");
            model.addAttribute("deptOptions", departmentService.findAllForDropdown(loginUser.getCompany()));
            model.addAttribute("employeeOptions", departmentService.findActiveEmployees(loginUser.getCompany()));
            return "department/edit";
        }

        redirectAttributes.addFlashAttribute("successMessage", "부서 수정에 성공했습니다.");
        return "redirect:/departments";
    }
}