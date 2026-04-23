package com.company.attendancemanagement.controller.pattern;

import com.company.attendancemanagement.dto.pattern.WorkPatternDetailDto;
import com.company.attendancemanagement.dto.pattern.WorkPatternMasterDto;
import com.company.attendancemanagement.dto.pattern.WorkPatternSaveRequest;
import com.company.attendancemanagement.service.pattern.WorkPatternService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/pattern")
public class WorkPatternController {

    private final WorkPatternService workPatternService;

    @GetMapping("/list")
    public String list(HttpSession session, Model model) {
        String company = (String) session.getAttribute("company");
        model.addAttribute("patterns", workPatternService.getPatternList(company));
        return "pattern/list";
    }

    @GetMapping("/new")
    public String createForm(HttpSession session, Model model) {
        String company = (String) session.getAttribute("company");

        WorkPatternSaveRequest request = new WorkPatternSaveRequest();

        WorkPatternMasterDto master = new WorkPatternMasterDto();
        master.setCompany(company);
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
        model.addAttribute("shiftCodes", workPatternService.getShiftCodes(company));
        return "pattern/form";
    }

    @GetMapping("/edit/{workPatternCode}")
    public String editForm(@PathVariable String workPatternCode,
                           HttpSession session,
                           Model model) {
        String company = (String) session.getAttribute("company");

        WorkPatternSaveRequest request = workPatternService.getPatternDetail(company, workPatternCode);

        model.addAttribute("pattern", request);
        model.addAttribute("shiftCodes", workPatternService.getShiftCodes(company));
        return "pattern/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("pattern") WorkPatternSaveRequest request,
                       HttpSession session,
                       Model model) {
        try {
            String company = (String) session.getAttribute("company");
            request.getMaster().setCompany(company);

            workPatternService.savePattern(request);
            return "redirect:/pattern/list";
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("shiftCodes", workPatternService.getShiftCodes((String) session.getAttribute("company")));
            return "pattern/form";
        }
    }
}