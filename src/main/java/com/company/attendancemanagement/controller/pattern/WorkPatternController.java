package com.company.attendancemanagement.controller.pattern;

import com.company.attendancemanagement.common.SessionConst;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.dto.pattern.WorkPatternDetailDto;
import com.company.attendancemanagement.dto.pattern.WorkPatternMasterDto;
import com.company.attendancemanagement.dto.pattern.WorkPatternSaveRequest;
import com.company.attendancemanagement.service.pattern.WorkPatternService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/pattern")
public class WorkPatternController {

    private final WorkPatternService workPatternService;

    @GetMapping("/list")
    public String list(HttpSession session, Model model) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return "redirect:/login";

        model.addAttribute("patterns", workPatternService.getPatternList(loginUser.getCompany()));
        return "pattern/list";
    }

    @GetMapping("/new")
    public String createForm(HttpSession session, Model model) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return "redirect:/login";

        WorkPatternSaveRequest request = new WorkPatternSaveRequest();
        WorkPatternMasterDto master = new WorkPatternMasterDto();
        master.setCompany(loginUser.getCompany());
        master.setUseYn("Y");
        request.setMaster(master);

        List<WorkPatternDetailDto> details = new ArrayList<>();
        for (int i = 1; i <= 31; i++) {
            WorkPatternDetailDto dto = new WorkPatternDetailDto();
            dto.setSeq(i);
            details.add(dto);
        }
        request.setDetails(details);

        model.addAttribute("pattern", request);
        model.addAttribute("shiftCodes", workPatternService.getShiftCodes(loginUser.getCompany()));
        model.addAttribute("isEdit", false);
        return "pattern/form";
    }

    @GetMapping("/edit/{workPatternCode}")
    public String editForm(@PathVariable String workPatternCode,
                           HttpSession session,
                           Model model) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return "redirect:/login";

        WorkPatternSaveRequest request = workPatternService.getPatternDetail(loginUser.getCompany(), workPatternCode);
        model.addAttribute("pattern", request);
        model.addAttribute("shiftCodes", workPatternService.getShiftCodes(loginUser.getCompany()));
        model.addAttribute("isEdit", true);
        return "pattern/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("pattern") WorkPatternSaveRequest request,
                       HttpSession session,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return "redirect:/login";

        try {
            request.getMaster().setCompany(loginUser.getCompany());
            workPatternService.savePattern(request);
            redirectAttributes.addFlashAttribute("successMessage", "패턴이 저장되었습니다.");
            return "redirect:/pattern/list";
        } catch (Exception e) {
            boolean isEdit = workPatternService.existsPattern(
                    loginUser.getCompany(), request.getMaster().getWorkPatternCode());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("shiftCodes", workPatternService.getShiftCodes(loginUser.getCompany()));
            model.addAttribute("isEdit", isEdit);
            return "pattern/form";
        }
    }

    @PostMapping("/delete")
    public String delete(@RequestParam String workPatternCode,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        LoginUserDto loginUser = getLoginUser(session);
        if (loginUser == null) return "redirect:/login";

        try {
            workPatternService.deletePattern(loginUser.getCompany(), workPatternCode);
            redirectAttributes.addFlashAttribute("successMessage",
                    "패턴 [" + workPatternCode + "]이(가) 삭제되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/pattern/list";
    }

    private LoginUserDto getLoginUser(HttpSession session) {
        return (LoginUserDto) session.getAttribute(SessionConst.LOGIN_USER);
    }
}