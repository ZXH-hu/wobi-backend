package com.bizhil.springbootinit.common;

import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

@Component
public class AvatarService {
//    private final String avatarDir = "src/main/resources/static/images"; // 本地图片目录
    private final String avatarDir = "/app/avatars"; // 本地图片目录

    public List<String> getAllAvatars() {
        List<String> avatars = new ArrayList<>();
        File folder = new File(avatarDir);
        for (File file : folder.listFiles()) {
            if (file.isFile() && file.getName().endsWith(".jpg")) {
                avatars.add(file.getName());
            }
        }
        return avatars;
    }

    public String getRandomAvatar() {
        List<String> avatars = getAllAvatars();
        Random rand = new Random();
        return avatars.get(rand.nextInt(avatars.size()));
    }
}
