package com.cloudcrypt.dto.setup;

public class SetupRequestDto {
    // Base de Datos
    private String dbHost;
    private String dbPort;
    private String dbName;
    private String dbUser;
    private String dbPass;

    // Almacenamiento y Cuotas
    private String maxQuotaBytes;
    private String maxFileSizeGb;

    private String uploadDir;

    // Gobernanza Criptográfica
    private String hashAlgo;
    private String symAlgo;
    private String asymKeySize;

    private String saltSuffix;

    // Cuenta del Administrador Inicial
    private String adminUsername;
    private String adminPassword;
    private String adminFullName;
    private String adminEmail;

    // Getters y Setters (o usa @Data si tienes Lombok)
    public String getDbHost() { return dbHost; }
    public void setDbHost(String dbHost) { this.dbHost = dbHost; }
    public String getDbPort() { return dbPort; }
    public void setDbPort(String dbPort) { this.dbPort = dbPort; }
    public String getDbName() { return dbName; }
    public void setDbName(String dbName) { this.dbName = dbName; }
    public String getDbUser() { return dbUser; }
    public void setDbUser(String dbUser) { this.dbUser = dbUser; }
    public String getDbPass() { return dbPass; }
    public void setDbPass(String dbPass) { this.dbPass = dbPass; }
    public String getMaxQuotaBytes() { return maxQuotaBytes; }
    public void setMaxQuotaBytes(String maxQuotaBytes) { this.maxQuotaBytes = maxQuotaBytes; }
    public String getMaxFileSizeGb() { return maxFileSizeGb; }
    public void setMaxFileSizeGb(String maxFileSizeGb) { this.maxFileSizeGb = maxFileSizeGb; }
    public String getUploadDir() { return uploadDir; }
    public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }
    public String getHashAlgo() { return hashAlgo; }
    public void setHashAlgo(String hashAlgo) { this.hashAlgo = hashAlgo; }
    public String getSymAlgo() { return symAlgo; }
    public void setSymAlgo(String symAlgo) { this.symAlgo = symAlgo; }
    public String getAsymKeySize() { return asymKeySize; }
    public void setAsymKeySize(String asymKeySize) { this.asymKeySize = asymKeySize; }
    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
    public String getAdminFullName() { return adminFullName; }
    public void setAdminFullName(String adminFullName) { this.adminFullName = adminFullName; }
    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }

    public String getSaltSuffix() { return saltSuffix; }
    public void setSaltSuffix(String saltSuffix) { this.saltSuffix = saltSuffix; }
}