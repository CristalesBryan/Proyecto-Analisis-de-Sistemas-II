package gt.municipalidad.qrds.entity;

public enum TipoCaso {
    Q("Queja", 15),
    R("Reclamo", 20),
    D("Denuncia", 30),
    S("Sugerencia", 30);

    private final String etiqueta;
    private final int diasHabiles;

    TipoCaso(String etiqueta, int diasHabiles) {
        this.etiqueta = etiqueta;
        this.diasHabiles = diasHabiles;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public int getDiasHabiles() {
        return diasHabiles;
    }

    public String plazoEstimado() {
        return diasHabiles + " días hábiles";
    }
}
