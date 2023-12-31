package multicampussa.laams.director.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import multicampussa.laams.director.dto.director.*;
import multicampussa.laams.director.service.DirectorService;
import multicampussa.laams.global.ApiResponse;
import multicampussa.laams.global.CustomExceptions;
import multicampussa.laams.home.member.jwt.JwtTokenProvider;
import multicampussa.laams.director.service.ImageUploadService;
import org.bson.json.JsonObject;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import springfox.documentation.annotations.ApiIgnore;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/director")
@Api(tags = "감독관")
public class DirectorController {

    private final DirectorService directorService;
    private final JwtTokenProvider jwtTokenProvider;
    private final ImageUploadService imageUploadService;

    // 시험 목록 조회
//    @GetMapping("/{directorNo}/exams")
//    public List<ExamListDto> getExams(@PathVariable Long directorNo){
//        return directorService.getExamList(directorNo);
//    }

    //이미지 업로드
    @ApiOperation(value = "추가 서류 등 업로드")
    @PostMapping(
            value = "/examinees/upload",
            consumes = "multipart/form-data")
    public String uploadImages(
            @ApiIgnore @RequestHeader String authorization,
            @RequestPart(value = "files") List<MultipartFile> files,
            @RequestParam("imageReason") String imageReason,
            @RequestParam("examNo") Long examNo,
            @RequestParam("examineeNo") Long examineeNo
    ) {
        String token = authorization.replace("Bearer", "");
        String authority = jwtTokenProvider.getAuthority(token);

        if (authority.equals("ROLE_DIRECTOR")) {
            try {
                for (MultipartFile file : files) {
                    byte[] imageBytes = file.getBytes();
                    String imageName = file.getOriginalFilename();
                    imageUploadService.uploadImageToS3(file, imageBytes, imageName, examNo, examineeNo, imageReason);
                }

                return "이미지 업로드 및 저장 성공!";
            } catch (IOException e) {
                // 파일 데이터를 읽어오는 과정에서 오류가 발생한 경우의 처리
                e.printStackTrace(); // 또는 로깅 등을 활용하여 예외를 기록
                return "이미지 업로드 실패: 파일 데이터를 읽어오는 도중 오류 발생";
            } catch (Exception e) {
                // 그 외의 예외 상황에 대한 처리
                e.printStackTrace(); // 또는 로깅 등을 활용하여 예외를 기록
                return "이미지 업로드 실패: " + e.getMessage();
            }
        } else {
            throw new CustomExceptions.UnauthorizedException("접근 권한이 없습니다.");
        }
    }


    // 얼굴 일치 비교
    @ApiOperation(value = "얼굴 일치 비교")
    @PostMapping(value = "/comparison")
    public ResponseEntity<ApiResponse<String>> comparePhoto(
            @ApiIgnore @RequestHeader String authorization,
            @RequestPart("existingPhoto") MultipartFile existingPhoto,
            @RequestPart("newPhoto") MultipartFile newPhoto,
            @RequestParam("examineeName") String applicantName,
            @RequestParam("examineeNo") String applicantNo
    ) {
        String token = authorization.replace("Bearer", "");
        String authority = jwtTokenProvider.getAuthority(token);
        if (authority.equals("ROLE_DIRECTOR")) {
            String result = directorService.compareFace(
                    existingPhoto,
                    newPhoto,
                    applicantName,
                    applicantNo
            );

            return new ResponseEntity<>(new ApiResponse<>(
                    "success",
                    HttpStatus.OK.value(),
                    result), HttpStatus.OK);

        } else {
            throw new CustomExceptions.UnauthorizedException("접근 권한이 없습니다.");
        }
    }

    // 파일 헤더 설정을 위한 메서드
    private HttpHeaders getMultipartHeaders(MultipartFile file) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf(file.getContentType()));
        headers.setContentDispositionFormData(file.getName(), file.getOriginalFilename());
        return headers;
    }



    @ApiOperation(value = "시험 월별 조회 및 일별 조회 (캘린더용)")
    @GetMapping("/{directorNo}/exams")
    public ResponseEntity<Map<String, Object>> getExamMonthDayList(@ApiIgnore @RequestHeader String authorization,
                                                                   @PathVariable Long directorNo,
                                                                   @RequestParam int year, @RequestParam int month, @RequestParam(value = "day", defaultValue = "0") int day){
        Map<String, Object> resultMap = new HashMap<>();
        try{
            String token  = authorization.replace("Bearer ", "");
            String authority = jwtTokenProvider.getAuthority(token);

            resultMap.put("message","시험을 성공적으로 조회했습니다.");
            resultMap.put("data", directorService.getExamMonthDayList(directorNo, year, month, day, authority));
            resultMap.put("code", HttpStatus.OK.value());
            resultMap.put("status", "success");
            return new ResponseEntity<>(resultMap, HttpStatus.OK);
        }catch (IllegalArgumentException e){
            resultMap.put("message", e.getMessage());
            resultMap.put("status", HttpStatus.BAD_REQUEST.value());
            return new ResponseEntity<>(resultMap, HttpStatus.BAD_REQUEST);
        }
    }

    @ApiOperation(value = "시험 상세정보 조회")
    @GetMapping("/exams/{examNo}")
    public ResponseEntity<Map<String, Object>> getExamInformation(@ApiIgnore @RequestHeader String authorization, @PathVariable Long examNo){
        Map<String, Object> resultMap = new HashMap<>();
        try{
            String token  = authorization.replace("Bearer ", "");
            String authority = jwtTokenProvider.getAuthority(token);
            String directorId = jwtTokenProvider.getId(token);

            ExamInformationDto examInformationDto = directorService.getExamInformation(examNo, authority, directorId);

            resultMap.put("message","시험 상세정보를 성공적으로 조회했습니다.");
            resultMap.put("data", examInformationDto);
            resultMap.put("code", HttpStatus.OK.value());
            resultMap.put("status", "success");
            return new ResponseEntity<>(resultMap, HttpStatus.OK);
        }catch (IllegalArgumentException e){
            resultMap.put("message", e.getMessage());
            resultMap.put("status", HttpStatus.BAD_REQUEST.value());
            return new ResponseEntity<>(resultMap, HttpStatus.BAD_REQUEST);
        }
    }

    @ApiOperation(value = "시험 응시자 목록 조회")
    @GetMapping("/exams/{examNo}/examinees")
    public ResponseEntity<Map<String, Object>> getExamExamineeList(@ApiIgnore @RequestHeader String authorization, @PathVariable Long examNo) {
        Map<String, Object> resultMap = new HashMap<>();
        try{
            String token  = authorization.replace("Bearer ", "");
            String authority = jwtTokenProvider.getAuthority(token);
            String directorId = jwtTokenProvider.getId(token);

            resultMap.put("message","시험 응시자 목록을 성공적으로 조회했습니다.");
            resultMap.put("data", directorService.getExamExamineeList(examNo, authority, directorId));
            resultMap.put("code", HttpStatus.OK.value());
            resultMap.put("status", "success");
            return new ResponseEntity<>(resultMap, HttpStatus.OK);
        }catch (IllegalArgumentException e){
            resultMap.put("message", e.getMessage());
            resultMap.put("status", HttpStatus.BAD_REQUEST.value());
            return new ResponseEntity<>(resultMap, HttpStatus.BAD_REQUEST);
        }
    }

    @ApiOperation(value = "시험 응시자 상세 조회")
    @GetMapping("/exams/{examNo}/examinees/{examineeNo}")
    public ResponseEntity<Map<String, Object>> getExamExaminee(@ApiIgnore @RequestHeader String authorization, @PathVariable Long examNo, @PathVariable Long examineeNo){
        Map<String, Object> resultMap = new HashMap<>();
        try{
            String token  = authorization.replace("Bearer ", "");
            String authority = jwtTokenProvider.getAuthority(token);
            String directorId = jwtTokenProvider.getId(token);


            ExamExamineeDto examExamineeDto = directorService.getExamExaminee(examNo, examineeNo, authority, directorId);

            resultMap.put("message","시험 응시자의 상세 정보를 성공적으로 조회했습니다.");
            resultMap.put("data", examExamineeDto);
            resultMap.put("code", HttpStatus.OK.value());
            resultMap.put("status", "success");
            return new ResponseEntity<>(resultMap, HttpStatus.OK);
        }catch (IllegalArgumentException e){
            resultMap.put("message", e.getMessage());
            resultMap.put("status", HttpStatus.BAD_REQUEST.value());
            return new ResponseEntity<>(resultMap, HttpStatus.BAD_REQUEST);
        }
    }

    @ApiOperation(value = "시험 응시자 현황 조회")
    @GetMapping("/exams/{examNo}/status")
    public ResponseEntity<Map<String, Object>> getExamStatus(@ApiIgnore @RequestHeader String authorization, @PathVariable Long examNo){
        Map<String, Object> resultMap = new HashMap<>();
        try {
            String token  = authorization.replace("Bearer ", "");
            String authority = jwtTokenProvider.getAuthority(token);
            String directorId = jwtTokenProvider.getId(token);

            ExamStatusDto examStatusDto = directorService.getExamStatus(examNo, authority, directorId);

            resultMap.put("message","시험 응시자의 현황을 성공적으로 조회했습니다.");
            resultMap.put("data", examStatusDto);
            resultMap.put("code", HttpStatus.OK.value());
            resultMap.put("status", "success");
            return new ResponseEntity<>(resultMap, HttpStatus.OK);
        }
        catch (IllegalArgumentException e){
            resultMap.put("message", e.getMessage());
            resultMap.put("status", HttpStatus.BAD_REQUEST.value());
            return new ResponseEntity<>(resultMap, HttpStatus.BAD_REQUEST);
        }
    }

    @ApiOperation(value = "출석 확인")
    @PutMapping("/exams/{examNo}/examinees/{examineeNo}/attendance")
    public ResponseEntity<Map<String, Object>> checkAttendance(@ApiIgnore @RequestHeader String authorization, @PathVariable Long examNo, @PathVariable Long examineeNo){
        Map<String, Object> resultMap = new HashMap<>();
        try {
            String token = authorization.replace("Bearer", "");
            String authority = jwtTokenProvider.getAuthority(token);
            String directorId = jwtTokenProvider.getId(token);

            CheckAttendanceDto checkAttendanceDto = directorService.checkAttendance(examNo, examineeNo, authority, directorId);
            resultMap.put("data", checkAttendanceDto);
            resultMap.put("message", "응시자의 출석이 확인되었습니다.");
            resultMap.put("code", HttpStatus.OK.value());
            resultMap.put("status", "success");
            return new ResponseEntity<>(resultMap, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            resultMap.put("message", e.getMessage());
            resultMap.put("status", HttpStatus.BAD_REQUEST.value());
            return new ResponseEntity<>(resultMap, HttpStatus.BAD_REQUEST);
        }
    }

    @ApiOperation(value = "서류 제출 확인")
    @PutMapping ("/exams/{examNo}/examinees/{examineeNo}/document")
    public ResponseEntity<Map<String, Object>> checkDocument(@ApiIgnore @RequestHeader String authorization, @PathVariable Long examNo, @PathVariable Long examineeNo, @RequestBody DocumentRequestDto documentRequestDto) {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            String token = authorization.replace("Bearer", "");
            String authority = jwtTokenProvider.getAuthority(token);
            String directorId = jwtTokenProvider.getId(token);

            CheckDocumentDto checkDocumentDto = directorService.checkDocument(examNo, examineeNo, documentRequestDto, authority, directorId);
            resultMap.put("data", checkDocumentDto);
            resultMap.put("message", "응시자의 서류 제출 여부가 확인되었습니다.");
            resultMap.put("code", HttpStatus.OK.value());
            resultMap.put("status", "success");
            return new ResponseEntity<>(resultMap, HttpStatus.OK);
        }catch (IllegalArgumentException e){
            resultMap.put("message", e.getMessage());
            resultMap.put("status", HttpStatus.BAD_REQUEST.value());
            return new ResponseEntity<>(resultMap, HttpStatus.BAD_REQUEST);
        }
    }

    @ApiOperation(value = "감독관 시험 배정 요청", notes = "로우 추가")
    @PostMapping("/exams/request")
    public ResponseEntity<Map<String, Object>> requestExamAssignment(@ApiIgnore @RequestHeader String authorization, @RequestBody Map<String, Long> examNo){
        Map<String, Object> resultMap = new HashMap<>();
        try {
            String token = authorization.replace("Bearer", "");
            String authority = jwtTokenProvider.getAuthority(token);
            String directorId = jwtTokenProvider.getId(token);
            Long centerNo = jwtTokenProvider.getCenterNo(token);

            Long examPk = examNo.get("examNo");

            directorService.requestExamAssignment(examPk, authority, directorId, centerNo);
            resultMap.put("message", "감독관의 시험 배정 요청이 정상적으로 처리 되었습니다.");
            resultMap.put("code", HttpStatus.OK.value());
            resultMap.put("status", "success");
            return new ResponseEntity<>(resultMap, HttpStatus.OK);
        }catch (IllegalArgumentException e){
            resultMap.put("message", e.getMessage());
            resultMap.put("status", HttpStatus.BAD_REQUEST.value());
            return new ResponseEntity<>(resultMap, HttpStatus.BAD_REQUEST);
        }
    }

    @ApiOperation(value = "보상 신청", notes = "컬럼 업데이트")
    @PostMapping("/exams/{examNo}/examinees/{examineeNo}/applyCompensation")
    public ResponseEntity<Map<String, Object>> applyCompensation(@ApiIgnore @RequestHeader String authorization, @PathVariable Long examNo, @PathVariable Long examineeNo, @RequestBody CompensationApplyDto compensationApplyDto){
        Map<String, Object> resultMap = new HashMap<>();
        try {
            String token = authorization.replace("Bearer", "");
            String authority = jwtTokenProvider.getAuthority(token);
            String directorId = jwtTokenProvider.getId(token);

            directorService.applyCompensation(examNo, examineeNo, compensationApplyDto ,authority, directorId);
            resultMap.put("message", "보상 신청이 되었습니다.");
            resultMap.put("code", HttpStatus.OK.value());
            resultMap.put("status", "success");
            return new ResponseEntity<>(resultMap, HttpStatus.OK);
        }catch (IllegalArgumentException e){
            resultMap.put("message", e.getMessage());
            resultMap.put("status", HttpStatus.BAD_REQUEST.value());
            return new ResponseEntity<>(resultMap, HttpStatus.BAD_REQUEST);
        }
    }


    @ApiOperation(value = "감독관 센터 도착 인증")
    @PostMapping("/exams/{examNo}/{directorNo}/attendance")
    public ResponseEntity<Map<String, Object>> attendacneDirector(@ApiIgnore @RequestHeader String authorization, @PathVariable Long examNo, @PathVariable Long directorNo, @RequestBody DirectorAttendanceRequestDto directorAttendacneRequestDto){
        Map<String, Object> resultMap = new HashMap<>();
        try {
            String token = authorization.replace("Bearer", "");
            String authority = jwtTokenProvider.getAuthority(token);
            String directorId = jwtTokenProvider.getId(token);

            DirectorAttendanceDto directorAttendanceDto = directorService.attendanceDirector(examNo, directorNo, directorAttendacneRequestDto, authority, directorId);
            resultMap.put("data", directorAttendanceDto);
            resultMap.put("message", "센터 도착이 인증되었습니다.");
            resultMap.put("code", HttpStatus.OK.value());
            resultMap.put("status", "success");
            return new ResponseEntity<>(resultMap, HttpStatus.OK);
        }catch (IllegalArgumentException e) {
            resultMap.put("message", e.getMessage());
            resultMap.put("status", HttpStatus.BAD_REQUEST.value());
            return new ResponseEntity<>(resultMap, HttpStatus.BAD_REQUEST);
        }
    }

    @ApiOperation(value = "감독관 센터 도착 인증 (홈화면)")
    @PostMapping("/exams/attendance/home")
    public ResponseEntity<Map<String, Object>> attendanceDirectorHome(@ApiIgnore @RequestHeader String authorization, @RequestBody DirectorAttendanceRequestDto directorAttendacneRequestDto){
        Map<String, Object> resultMap = new HashMap<>();
        try {
            String token = authorization.replace("Bearer ", "");
            String authority = jwtTokenProvider.getAuthority(token);
            String directorId = jwtTokenProvider.getId(token);

            DirectorAttendanceDto directorAttendanceDto = directorService.attendanceDirectorHome(directorAttendacneRequestDto, authority, directorId);
            resultMap.put("data", directorAttendanceDto);
            resultMap.put("message", "센터 도착이 인증되었습니다.");
            resultMap.put("code", HttpStatus.OK.value());
            resultMap.put("status", "success");
            return new ResponseEntity<>(resultMap, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            resultMap.put("message", e.getMessage());
            resultMap.put("status", HttpStatus.BAD_REQUEST.value());
            return new ResponseEntity<>(resultMap, HttpStatus.BAD_REQUEST);
        }
    }

    @ApiOperation(value = "신청 안 한 시험과 승인 안 된 시험 목록 조회", notes = "내가 속한 센터 시험 중 내가 아직 신청 요청 안 보내고, 꽉 차지 않은 시험 + 내가 신청했지만 승인 안된 시험 목록")
    @GetMapping("/exams/unapproved")
    public ResponseEntity<Map<String, Object>> UnappliedAndUnapprovedExamList(@ApiIgnore @RequestHeader String authorization,
                                                                              @RequestParam int year, @RequestParam int month, @RequestParam(value = "day", defaultValue = "0") int day) {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            String token  = authorization.replace("Bearer ", "");
            String authority = jwtTokenProvider.getAuthority(token);
            String directorId = jwtTokenProvider.getId(token);
            Long centerNo = jwtTokenProvider.getCenterNo(token);

            resultMap.put("message","시험을 성공적으로 조회했습니다.");
            resultMap.put("data", directorService.unappliedAndUnapprovedExamList(authority, directorId, centerNo, year, month, day));
            resultMap.put("code", HttpStatus.OK.value());
            resultMap.put("status", "success");
            return new ResponseEntity<>(resultMap, HttpStatus.OK);
        }catch (IllegalArgumentException e){
            resultMap.put("message", e.getMessage());
            resultMap.put("status", HttpStatus.BAD_REQUEST.value());
            return new ResponseEntity<>(resultMap, HttpStatus.BAD_REQUEST);
        }
    }

    @ApiOperation(value = "신청 가능한 시험 목록 조회", notes = "내가 속한 센터 시험 중 신청하지 않았고 신청이 가능한 (꽉 차지 않은) 시험 목록")
    @GetMapping("/exams/possibleApply")
    public ResponseEntity<Map<String, Object>> possibleToApplyExamList(@ApiIgnore @RequestHeader String authorization,
                                                                       @RequestParam int year, @RequestParam int month, @RequestParam(value = "day", defaultValue = "0") int day){
        Map<String, Object> resultMap = new HashMap<>();
        try {
            String token  = authorization.replace("Bearer ", "");
            String authority = jwtTokenProvider.getAuthority(token);
            String directorId = jwtTokenProvider.getId(token);
            Long centerNo = jwtTokenProvider.getCenterNo(token);

            resultMap.put("message","시험을 성공적으로 조회했습니다.");
            resultMap.put("data", directorService.possibleToApplyExamList(authority, directorId, centerNo, year, month, day));
            resultMap.put("code", HttpStatus.OK.value());
            resultMap.put("status", "success");
            return new ResponseEntity<>(resultMap, HttpStatus.OK);
        }catch (IllegalArgumentException e){
            resultMap.put("message", e.getMessage());
            resultMap.put("status", HttpStatus.BAD_REQUEST.value());
            return new ResponseEntity<>(resultMap, HttpStatus.BAD_REQUEST);
        }
    }

}
