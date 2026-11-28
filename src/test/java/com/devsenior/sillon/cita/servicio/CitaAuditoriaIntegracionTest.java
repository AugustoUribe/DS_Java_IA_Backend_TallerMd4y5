
package com.devsenior.sillon.cita.servicio;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.devsenior.sillon.cita.dto.RespuestaCita;
import com.devsenior.sillon.cita.dto.SolicitudCambiarEstadoCita;
import com.devsenior.sillon.cita.dto.SolicitudCrearCita;
import com.devsenior.sillon.cita.modelo.EstadoCita;
import com.devsenior.sillon.cita.modelo.OrigenCita;
import com.devsenior.sillon.salon.modelo.ConfiguracionSalon;
import com.devsenior.sillon.salon.modelo.Plan;
import com.devsenior.sillon.salon.modelo.Salon;
import com.devsenior.sillon.salon.repositorio.SalonRepositorio;
import com.devsenior.sillon.servicio.modelo.Servicio;
import com.devsenior.sillon.servicio.repositorio.ServicioRepositorio;
import com.devsenior.sillon.usuario.modelo.Rol;
import com.devsenior.sillon.usuario.modelo.Usuario;
import com.devsenior.sillon.usuario.repositorio.UsuarioRepositorio;

@SpringBootTest
@Transactional
class CitaAuditoriaIntegracionTest {

    @Autowired
    private CitaServicio citaServicio;

    @Autowired
    private SalonRepositorio salonRepositorio;

    @Autowired
    private ServicioRepositorio servicioRepositorio;

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock reloj;

    @Test
    void confirmarReservaPublicaActualizaFechaPersistida() {

        // Datos propios de esta prueba.
        // La transaccion se revierte al finalizar.
        Salon salon = new Salon();
        salon.setNombre("Salon Prueba Auditoria");
        salon.setSlug("auditoria-" + UUID.randomUUID());
        salon.setCiudad("Bogota");
        salon.setDireccion("Direccion de prueba");
        salon.setTelefono("3001234567");
        salon.setPlan(Plan.GRATIS);
        salon.setCreditosIa(5);
        salon.setConfiguracion(
                ConfiguracionSalon.predeterminada());
        salon.setFechaCreacion(LocalDateTime.now(reloj));

        salon = salonRepositorio.saveAndFlush(salon);

        Servicio servicio = new Servicio();
        servicio.setSalon(salon);
        servicio.setNombre("Corte de prueba");
        servicio.setDuracionMinutos(30);
        servicio.setPrecio(25000);
        servicio.setActivo(true);

        servicio = servicioRepositorio.saveAndFlush(servicio);

        Usuario estilista = new Usuario();
        estilista.setSalon(salon);
        estilista.setNombre("Estilista Auditoria");
        estilista.setEmail(
                "auditoria-" + UUID.randomUUID() + "@example.com");
        estilista.setPassword("PasswordPrueba123");
        estilista.setRol(Rol.ESTILISTA);
        estilista.setActivo(true);
        estilista.setServicios(List.of(servicio));

        estilista = usuarioRepositorio.saveAndFlush(estilista);

        // Elegimos un lunes futuro, dentro del horario del salon.
        LocalDate lunes = LocalDate.now(reloj)
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        for (int i = 0; i < 8; i++) {

            LocalDateTime inicio = lunes.atTime(10, 0)
                    .plusMinutes(i * 30L);

            SolicitudCrearCita solicitud = new SolicitudCrearCita(
                    "Cliente Auditoria " + i,
                    "300123" + String.format("%04d", i),
                    servicio.getId(),
                    estilista.getId(),
                    inicio,
                    "Prueba auditoria");

            // 1. Crear una reserva publica.
            RespuestaCita creada = citaServicio.crearReservaPublica(
                    salon.getSlug(), solicitud);

            assertEquals(EstadoCita.PENDIENTE, creada.estado());
            assertEquals(OrigenCita.EN_LINEA, creada.origen());

            // 2. Confirmarla inmediatamente.
            RespuestaCita confirmada = citaServicio.cambiarEstado(
                    salon.getId(),
                    999L,
                    Rol.DUENO,
                    creada.id(),
                    new SolicitudCambiarEstadoCita(
                            EstadoCita.CONFIRMADA));

            assertEquals(
                    EstadoCita.CONFIRMADA,
                    confirmada.estado());

            // 3. Leer las fechas directamente de MySQL.
            LocalDateTime[] fechas = jdbcTemplate.queryForObject(
                    """
                            SELECT fecha_creacion, fecha_actualizacion
                            FROM citas
                            WHERE id = ?
                            """,
                    (rs, fila) -> new LocalDateTime[] {
                            rs.getTimestamp("fecha_creacion")
                                    .toLocalDateTime(),
                            rs.getTimestamp("fecha_actualizacion")
                                    .toLocalDateTime()
                    },
                    creada.id());

            assertNotNull(fechas);

            System.out.printf(
                    "Cita %d | Creacion: %s | Actualizacion: %s%n",
                    creada.id(),
                    fechas[0],
                    fechas[1]);

            assertTrue(
                    fechas[1].isAfter(fechas[0]),
                    "La actualizacion debe ser posterior "
                            + "a la creacion. Cita ID: "
                            + creada.id());
        }
    }
}
