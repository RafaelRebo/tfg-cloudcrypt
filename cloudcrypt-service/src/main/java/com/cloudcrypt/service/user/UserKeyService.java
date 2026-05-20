package com.cloudcrypt.service.user;

import com.cloudcrypt.dto.user.KeyRequestDto;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.model.UserKeyEntity;
import com.cloudcrypt.repository.keys.UserKeyRepository;
import com.cloudcrypt.repository.user.UserRepository;
import com.cloudcrypt.exceptions.InstanceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserKeyService {

    private static final Logger log = LoggerFactory.getLogger(UserKeyService.class);

    private final UserKeyRepository userKeyRepository;
    private final UserRepository userRepository;

    public UserKeyService(UserKeyRepository userKeyRepository, UserRepository userRepository) {
        this.userKeyRepository = userKeyRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void registerKeys(String username, KeyRequestDto request){
        UserEntity user = userRepository.findByUsername(username);
        if (user == null) throw new InstanceNotFoundException("Usuario no encontrado");

        log.info("REGISTRO: Guardando claves asimétricas en base de datos para el usuario [{}].", username);
        UserKeyEntity userKeys = userKeyRepository.findById(user.getId())
                .orElse(new UserKeyEntity());

        userKeys.setUser(user);
        userKeys.setPublicKey(request.getPublicKey());
        userKeys.setEncryptedPrivateKey(request.getEncryptedPrivateKey());

        userKeyRepository.saveAndFlush(userKeys);
    }

    public Map<String, Object> getPublicInfo(String username){
        log.debug("OPERACIÓN: Solicitada clave pública para @{}.", username);
        UserEntity user = userRepository.findByUsername(username);
        if (user == null) throw new InstanceNotFoundException("Usuario no encontrado");

        UserKeyEntity keys = userKeyRepository.findById(user.getId())
                .orElseThrow(() -> {
                    log.warn("OPERACIÓN: El usuario @{} carece de claves.", username);
                    return new InstanceNotFoundException("El usuario no tiene llaves generadas");
                });

        Map<String, Object> response = new HashMap<>();
        response.put("publicKey", keys.getPublicKey());
        return response;
    }

    public String getEncryptedPrivateKey(String username){
        log.info("OPERACIÓN: Descargando clave privada RSA por orden de @{}.", username);
        UserEntity user = userRepository.findByUsername(username);
        if (user == null) throw new InstanceNotFoundException("Usuario no encontrado");

        return userKeyRepository.findById(user.getId())
                .map(UserKeyEntity::getEncryptedPrivateKey)
                .orElseThrow(() -> new InstanceNotFoundException("El usuario no tiene llaves registradas"));
    }

    @Transactional
    public void updatePrivateKey(String username, String newEncryptedPrivateKey) {
        log.warn("OPERACIÓN: Sobreescribiendo clave privada RSA para @{} por rotación de claves.", username);
        UserEntity user = userRepository.findByUsername(username);
        if (user == null) throw new InstanceNotFoundException("Usuario no encontrado");

        UserKeyEntity userKeys = userKeyRepository.findById(user.getId())
                .orElseThrow(() -> new InstanceNotFoundException("El usuario no posee un llavero relacional instanciado"));

        userKeys.setEncryptedPrivateKey(newEncryptedPrivateKey);
        userKeyRepository.save(userKeys);
    }
}