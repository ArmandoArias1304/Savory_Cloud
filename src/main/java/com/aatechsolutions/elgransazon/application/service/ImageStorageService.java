package com.aatechsolutions.elgransazon.application.service;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {
    /**
     * Guarda una imagen, la convierte a formato WEBP y retorna la ruta relativa
     * @param file archivo de imagen a guardar
     * @param folder carpeta destino (ej: "menu-items")
     * @param fileName nombre base del archivo (opcional, se limpiará de caracteres especiales)
     * @return ruta relativa del archivo guardado (ej: "/uploads/menu-items/producto.webp")
     */
    String saveImage(MultipartFile file, String folder, String fileName) throws Exception;
    
    /**
     * Elimina una imagen del sistema de archivos
     * @param imagePath ruta relativa de la imagen (ej: "/uploads/menu-items/12345.webp")
     */
    void deleteImage(String imagePath);
    
    /**
     * Valida que el archivo sea una imagen válida
     * @param file archivo a validar
     * @return true si es una imagen válida
     */
    boolean isValidImage(MultipartFile file);
}
