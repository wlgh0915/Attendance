package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.annual.AnnualLeaveUsageCandidateDto;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface AnnualLeaveMapper {

    int ensureAnnualDetail(@Param("company") String company,
                           @Param("yyyy") int yyyy,
                           @Param("empCode") String empCode);

    BigDecimal findTotalDay(@Param("company") String company,
                            @Param("yyyy") int yyyy,
                            @Param("empCode") String empCode);

    int updateUsage(@Param("company") String company,
                    @Param("yyyy") int yyyy,
                    @Param("empCode") String empCode,
                    @Param("useDay") BigDecimal useDay);

    List<AnnualLeaveUsageCandidateDto> findUsageCandidates(@Param("company") String company,
                                                           @Param("empCode") String empCode,
                                                           @Param("yyyy") int yyyy,
                                                           @Param("includeSubmitted") boolean includeSubmitted,
                                                           @Param("excludeRequestId") String excludeRequestId);
}
