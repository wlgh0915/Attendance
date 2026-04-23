package com.company.attendancemanagement.mapper.pattern;

import com.company.attendancemanagement.dto.pattern.ShiftCodeDto;
import com.company.attendancemanagement.dto.pattern.WorkPatternDetailDto;
import com.company.attendancemanagement.dto.pattern.WorkPatternMasterDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkPatternMapper {

    List<WorkPatternMasterDto> findPatternList(@Param("company") String company);

    WorkPatternMasterDto findPatternMaster(@Param("company") String company,
                                           @Param("workPatternCode") String workPatternCode);

    List<WorkPatternDetailDto> findPatternDetails(@Param("company") String company,
                                                  @Param("workPatternCode") String workPatternCode);

    List<ShiftCodeDto> findShiftCodes(@Param("company") String company);

    int existsPatternCode(@Param("company") String company,
                          @Param("workPatternCode") String workPatternCode);

    int insertPatternMaster(WorkPatternMasterDto dto);

    int updatePatternMaster(WorkPatternMasterDto dto);

    int deletePatternDetails(@Param("company") String company,
                             @Param("workPatternCode") String workPatternCode);

    int insertPatternDetail(WorkPatternDetailDto dto);

    int countPatternInUse(@Param("company") String company,
                          @Param("workPatternCode") String workPatternCode);

    int deletePatternMaster(@Param("company") String company,
                            @Param("workPatternCode") String workPatternCode);
}