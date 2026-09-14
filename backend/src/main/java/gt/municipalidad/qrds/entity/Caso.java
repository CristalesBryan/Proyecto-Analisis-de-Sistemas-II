package gt.municipalidad.qrds.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "casos")
public class Caso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_seguimiento", nullable = false, unique = true, length = 20)
    private String codigoSeguimiento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_caso", nullable = false, length = 1)
    private TipoCaso tipoCaso;

    @Column(name = "nombre_ciudadano", length = 150)
    private String nombreCiudadano;

    @Column(name = "email_ciudadano", length = 150)
    private String emailCiudadano;

    @Column(length = 20)
    private String telefono;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_dependencia_id", nullable = false)
    private AreaDependencia areaDependencia;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "denunciado_nombre", length = 150)
    private String denunciadoNombre;

    @Column(name = "es_anonimo", nullable = false)
    private boolean esAnonimo = false;

    @Column(name = "acepta_privacidad", nullable = false)
    private boolean aceptaPrivacidad = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCaso estado = EstadoCaso.RECIBIDO;

    @Column(name = "avance_porcentaje", nullable = false)
    private int avancePorcentaje = 0;

    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro = Instant.now();

    @Column(name = "fecha_ultima_actualizacion", nullable = false)
    private Instant fechaUltimaActualizacion = Instant.now();

    @Column(name = "fecha_limite_respuesta")
    private LocalDate fechaLimiteRespuesta;

    @Column(name = "ip_registro", length = 45)
    private String ipRegistro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agente_asignado_id")
    private Usuario agenteAsignado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Prioridad prioridad = Prioridad.MEDIA;

    @Column(nullable = false)
    private boolean escalado = false;

    @Column(name = "fecha_prorroga")
    private LocalDate fechaProrroga;

    public Long getId() {
        return id;
    }

    public String getCodigoSeguimiento() {
        return codigoSeguimiento;
    }

    public void setCodigoSeguimiento(String codigoSeguimiento) {
        this.codigoSeguimiento = codigoSeguimiento;
    }

    public TipoCaso getTipoCaso() {
        return tipoCaso;
    }

    public void setTipoCaso(TipoCaso tipoCaso) {
        this.tipoCaso = tipoCaso;
    }

    public String getNombreCiudadano() {
        return nombreCiudadano;
    }

    public void setNombreCiudadano(String nombreCiudadano) {
        this.nombreCiudadano = nombreCiudadano;
    }

    public String getEmailCiudadano() {
        return emailCiudadano;
    }

    public void setEmailCiudadano(String emailCiudadano) {
        this.emailCiudadano = emailCiudadano;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public AreaDependencia getAreaDependencia() {
        return areaDependencia;
    }

    public void setAreaDependencia(AreaDependencia areaDependencia) {
        this.areaDependencia = areaDependencia;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDenunciadoNombre() {
        return denunciadoNombre;
    }

    public void setDenunciadoNombre(String denunciadoNombre) {
        this.denunciadoNombre = denunciadoNombre;
    }

    public boolean isEsAnonimo() {
        return esAnonimo;
    }

    public void setEsAnonimo(boolean esAnonimo) {
        this.esAnonimo = esAnonimo;
    }

    public boolean isAceptaPrivacidad() {
        return aceptaPrivacidad;
    }

    public void setAceptaPrivacidad(boolean aceptaPrivacidad) {
        this.aceptaPrivacidad = aceptaPrivacidad;
    }

    public EstadoCaso getEstado() {
        return estado;
    }

    public void setEstado(EstadoCaso estado) {
        this.estado = estado;
    }

    public int getAvancePorcentaje() {
        return avancePorcentaje;
    }

    public void setAvancePorcentaje(int avancePorcentaje) {
        this.avancePorcentaje = avancePorcentaje;
    }

    public Instant getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(Instant fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public Instant getFechaUltimaActualizacion() {
        return fechaUltimaActualizacion;
    }

    public void setFechaUltimaActualizacion(Instant fechaUltimaActualizacion) {
        this.fechaUltimaActualizacion = fechaUltimaActualizacion;
    }

    public LocalDate getFechaLimiteRespuesta() {
        return fechaLimiteRespuesta;
    }

    public void setFechaLimiteRespuesta(LocalDate fechaLimiteRespuesta) {
        this.fechaLimiteRespuesta = fechaLimiteRespuesta;
    }

    public String getIpRegistro() {
        return ipRegistro;
    }

    public void setIpRegistro(String ipRegistro) {
        this.ipRegistro = ipRegistro;
    }

    public Usuario getAgenteAsignado() {
        return agenteAsignado;
    }

    public void setAgenteAsignado(Usuario agenteAsignado) {
        this.agenteAsignado = agenteAsignado;
    }

    public Prioridad getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(Prioridad prioridad) {
        this.prioridad = prioridad;
    }

    public boolean isEscalado() {
        return escalado;
    }

    public void setEscalado(boolean escalado) {
        this.escalado = escalado;
    }

    public LocalDate getFechaProrroga() {
        return fechaProrroga;
    }

    public void setFechaProrroga(LocalDate fechaProrroga) {
        this.fechaProrroga = fechaProrroga;
    }

    public void tocar() {
        this.fechaUltimaActualizacion = Instant.now();
    }
}
