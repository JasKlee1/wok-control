package com.wokcontrol.menu.controller.web;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.menu.controller.api.ImagenRespuesta;
import com.wokcontrol.menu.service.ImagenService;
import com.wokcontrol.menu.service.ImagenService.Credito;
import com.wokcontrol.menu.service.ImagenService.Destino;

@Controller
@RequestMapping("/menu")
public class ImagenWebController {

    private final ImagenService servicio;

    public ImagenWebController(ImagenService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/imagenes/{imagenId}")
    public ResponseEntity<?> imagen(@PathVariable String imagenId) {
        return ImagenRespuesta.de(servicio.abrir(imagenId));
    }

    @GetMapping("/imagenes/creditos")
    public String creditos(Model model) {
        model.addAttribute("creditos", servicio.creditos());
        return "menu/creditos";
    }

    @PostMapping("/{destino}/{id}/imagen")
    public String asignar(@PathVariable String destino, @PathVariable String id, @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(required = false) String autor, @RequestParam(required = false) String licencia,
            @RequestParam(required = false) String fuenteUrl, RedirectAttributes flash) throws IOException {
        Destino d = destino(destino);
        try {
            servicio.asignar(d, id, archivo.getBytes(), new Credito(autor, licencia, null, fuenteUrl));
            flash.addFlashAttribute("exito", "Foto actualizada");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return volver(d, id);
    }

    @PostMapping("/{destino}/{id}/imagen/quitar")
    public String quitar(@PathVariable String destino, @PathVariable String id, RedirectAttributes flash) {
        Destino d = destino(destino);
        servicio.quitar(d, id);
        flash.addFlashAttribute("exito", "Foto eliminada");
        return volver(d, id);
    }

    private static Destino destino(String ruta) {
        return switch (ruta) {
            case "platos" -> Destino.PLATO;
            case "combos" -> Destino.COMBO;
            case "categorias" -> Destino.CATEGORIA;
            default -> throw new com.wokcontrol.common.exception.RecursoNoEncontradoException("Página", ruta);
        };
    }

    private static String volver(Destino d, String id) {
        return switch (d) {
            case PLATO -> "redirect:/menu/platos/" + id;
            case COMBO -> "redirect:/menu/combos";
            case CATEGORIA -> "redirect:/menu/categorias";
        };
    }
}
