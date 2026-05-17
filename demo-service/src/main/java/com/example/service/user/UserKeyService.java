package com.example.service.user;

import com.example.dto.user.KeyRequestDto;
import com.example.model.UserEntity;
import com.example.model.UserKeyEntity;
import com.example.repository.keys.UserKeyRepository;
import com.example.repository.user.UserRepository;
import com.example.exceptions.InstanceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserKeyService {

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

        UserKeyEntity userKeys = userKeyRepository.findById(user.getId())
                .orElse(new UserKeyEntity());

        userKeys.setUser(user);
        userKeys.setPublicKey(request.getPublicKey());
        userKeys.setEncryptedPrivateKey(request.getEncryptedPrivateKey());

        userKeyRepository.saveAndFlush(userKeys);
    }

    public Map<String, Object> getPublicInfo(String username){
        UserEntity user = userRepository.findByUsername(username);
        if (user == null) throw new InstanceNotFoundException("Usuario no encontrado");

        UserKeyEntity keys = userKeyRepository.findById(user.getId())
                .orElseThrow(() -> new InstanceNotFoundException("El usuario no tiene llaves generadas"));

        Map<String, Object> response = new HashMap<>();
        response.put("publicKey", keys.getPublicKey());
        return response;
    }

    public String getEncryptedPrivateKey(String username){
        UserEntity user = userRepository.findByUsername(username);
        if (user == null) throw new InstanceNotFoundException("Usuario no encontrado");

        return userKeyRepository.findById(user.getId())
                .map(UserKeyEntity::getEncryptedPrivateKey)
                .orElseThrow(() -> new InstanceNotFoundException("El usuario no tiene llaves registradas"));
    }
}