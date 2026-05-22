package com.cloudcrypt.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InstanceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(InstanceNotFoundException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InputValidationException.class)
    public ResponseEntity<?> handleInputValidation(InputValidationException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(FileAccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(FileAccessDeniedException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<?> handleQuota(QuotaExceededException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<?> handleUnauthorized(InvalidCredentialsException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<?> handleConflict(UserAlreadyExistsException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        ex.printStackTrace();
        return buildResponse("Conflicto: El registro ya existe o viola las restricciones del sistema.",
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InternalStorageException.class)
    public ResponseEntity<?> handleInternalStorage(InternalStorageException ex) {
        return buildResponse("Se ha producido un error interno al procesar el archivo.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception ex) {
        ex.printStackTrace();
        return buildResponse("Error interno del servidor", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(String message, HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", message);
        body.put("status", status.value());
        return new ResponseEntity<>(body, status);
    }
}