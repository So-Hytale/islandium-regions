package com.islandium.regions.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.islandium.core.api.permission.PermissionService;
import com.islandium.core.api.permission.Rank;
import com.islandium.core.api.util.ColorUtil;
import com.islandium.core.IslandiumPlugin;
import com.islandium.regions.RegionsPlugin;
import com.islandium.regions.flag.RegionFlag;
import com.islandium.regions.model.BoundingBox;
import com.islandium.regions.model.RegionImpl;
import com.islandium.regions.service.RegionService;
import com.islandium.regions.shape.CuboidShape;
import com.islandium.regions.shape.CylinderShape;
import com.islandium.regions.shape.GlobalShape;
import com.islandium.regions.shape.RegionShape;
import com.islandium.regions.util.RegionPermissionChecker;
import com.islandium.regions.util.SelectionHelper;
import com.islandium.regions.visualization.RegionVisualizationService;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Page principale de gestion des regions - Version 2.
 * Le layout est defini dans RegionMainPageV2.ui
 * Cette classe gere uniquement les donnees dynamiques et les events.
 */
public class RegionMainPage extends InteractiveCustomUIPage<RegionMainPage.PageData> {

    private static final String UI_PATH = "Pages/Regions/RegionMainPageV2.ui";

    // Couleurs pour le contenu dynamique
    private static final String COLOR_GOLD = "#ffd700";
    private static final String COLOR_GRAY = "#808080";
    private static final String COLOR_BG_PANEL = "#151d28";

    private final RegionsPlugin plugin;
    private final RegionService regionService;
    private final String worldName;

    // Etat
    private String currentPage = "home";
    private String selectedRegionName = null;
    private boolean confirmDelete = false;
    private String createShapeType = "cuboid"; // "cuboid" ou "cylinder"

    // Etat onglets flags
    private String currentFlagsTab = "region"; // "region", "group", "player"
    private String selectedRankName = null;
    private UUID selectedPlayerUuid = null;
    private String selectedPlayerName = null;

    // References
    private Ref<EntityStore> currentRef;
    private Store<EntityStore> currentStore;

    // Cache
    private final Map<UUID, String> playerNameCache = new ConcurrentHashMap<>();

    public RegionMainPage(@Nonnull PlayerRef playerRef, @Nonnull RegionsPlugin plugin, @Nonnull String worldName) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.regionService = plugin.getRegionService();
        this.worldName = worldName;
    }

    // ===========================================
    // BUILD
    // ===========================================

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        this.currentRef = ref;
        this.currentStore = store;

        // Charger le layout depuis le fichier .ui
        cmd.append(UI_PATH);

        // Lier tous les events
        bindAllEvents(event);

        // Charger la page actuelle (pas forcément home si on fait un rebuild)
        showPage(currentPage, cmd, event);
    }

    // ===========================================
    // BINDING DES EVENTS
    // ===========================================

    private void bindAllEvents(UIEventBuilder event) {
        // Navigation
        event.addEventBinding(CustomUIEventBindingType.Activating, "#NavBtnHome", EventData.of("NavTo", "home"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#NavBtnList", EventData.of("NavTo", "list"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#NavBtnCreate", EventData.of("NavTo", "create"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnToggleBypass", EventData.of("Action", "toggleBypass"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "close"), false);

        // Page Home - Global region
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnCreateGlobal", EventData.of("Action", "createGlobal"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnEditGlobal", EventData.of("Action", "editGlobal"), false);

        // Page Create
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnCancelCreate", EventData.of("NavTo", "list"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnShapeCuboid", EventData.of("Action", "setShapeCuboid"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnShapeCylinder", EventData.of("Action", "setShapeCylinder"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnConfirmCreate",
            EventData.of("Action", "createRegion")
                .append("@RegionName", "#InputRegionName.Value")
                .append("@RegionPriority", "#InputRegionPriority.Value")
                .append("@CylinderRadius", "#InputCylinderRadius.Value")
                .append("@CylinderHeight", "#InputCylinderHeight.Value"), false);

        // Page Editor
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnTeleport", EventData.of("Action", "teleport"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnShowZone", EventData.of("Action", "showZone"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnChangePriority",
            EventData.of("Action", "changePriority").append("@NewPriority", "#InputNewPriority.Value"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnUpdateCylinder",
            EventData.of("Action", "updateCylinder")
                .append("@NewRadius", "#EditorCylinderRadius.Value")
                .append("@NewHeight", "#EditorCylinderHeight.Value"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnEditFlags", EventData.of("NavTo", "flags"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnEditMembers", EventData.of("NavTo", "members"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnDeleteRegion", EventData.of("Action", "deleteRegion"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnConfirmDelete", EventData.of("Action", "confirmDelete"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnCancelDelete", EventData.of("Action", "cancelDelete"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnBackToList", EventData.of("NavTo", "list"), false);

        // Page Flags
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnBackFromFlags", EventData.of("NavTo", "editor"), false);

        // Onglets Flags
        event.addEventBinding(CustomUIEventBindingType.Activating, "#TabRegion", EventData.of("FlagsTab", "region"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#TabGroup", EventData.of("FlagsTab", "group"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#TabPlayer", EventData.of("FlagsTab", "player"), false);

        // Onglet Joueur - recherche
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnSearchPlayer",
            EventData.of("Action", "searchPlayer").append("@PlayerSearch", "#InputPlayerSearch.Value"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnClearPlayerSelection", EventData.of("Action", "clearPlayerSelection"), false);

        // Page Members
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnBackFromMembers", EventData.of("NavTo", "editor"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnAddMember",
            EventData.of("Action", "addMember").append("@MemberName", "#InputMemberName.Value"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BtnAddOwner",
            EventData.of("Action", "addOwner").append("@MemberName", "#InputMemberName.Value"), false);
    }

    // ===========================================
    // NAVIGATION
    // ===========================================

    private void showPage(String page, UICommandBuilder cmd, UIEventBuilder event) {
        this.currentPage = page;

        // Cacher toutes les pages
        cmd.set("#PageHome.Visible", false);
        cmd.set("#PageList.Visible", false);
        cmd.set("#PageCreate.Visible", false);
        cmd.set("#PageEditor.Visible", false);
        cmd.set("#PageFlags.Visible", false);
        cmd.set("#PageMembers.Visible", false);

        // Mettre à jour les styles des onglets de navigation
        updateNavBarStyle(cmd, page);

        // Mettre à jour l'état du bouton bypass
        updateBypassButton(cmd);

        // Afficher la page demandee et charger ses donnees
        switch (page) {
            case "home" -> {
                cmd.set("#PageHome.Visible", true);
                loadHomeData(cmd);
            }
            case "list" -> {
                cmd.set("#PageList.Visible", true);
                loadListData(cmd, event);
            }
            case "create" -> {
                cmd.set("#PageCreate.Visible", true);
                loadCreateData(cmd);
            }
            case "editor" -> {
                cmd.set("#PageEditor.Visible", true);
                loadEditorData(cmd, event);
            }
            case "flags" -> {
                cmd.set("#PageFlags.Visible", true);
                loadFlagsData(cmd, event);
            }
            case "members" -> {
                cmd.set("#PageMembers.Visible", true);
                loadMembersData(cmd, event);
            }
        }
    }

    /**
     * Met à jour le style des boutons de navigation selon la page active.
     * Utilise Style.Default.Background et Style.Default.LabelStyle pour les TextButton.
     */
    private void updateNavBarStyle(UICommandBuilder cmd, String activePage) {
        // Couleurs: actif = #3a5a7a, inactif = #1f2d3f
        // Texte: actif = #ffffff bold, inactif = #96a9be normal

        boolean homeActive = "home".equals(activePage);
        boolean listActive = "list".equals(activePage) || "editor".equals(activePage) || "flags".equals(activePage) || "members".equals(activePage);
        boolean createActive = "create".equals(activePage);

        // Bouton Accueil
        cmd.set("#NavBtnHome.Style.Default.Background", homeActive ? "#3a5a7a" : "#1f2d3f");
        cmd.set("#NavBtnHome.Style.Default.LabelStyle.TextColor", homeActive ? "#ffffff" : "#96a9be");
        cmd.set("#NavBtnHome.Style.Default.LabelStyle.RenderBold", homeActive);

        // Bouton Liste
        cmd.set("#NavBtnList.Style.Default.Background", listActive ? "#3a5a7a" : "#1f2d3f");
        cmd.set("#NavBtnList.Style.Default.LabelStyle.TextColor", listActive ? "#ffffff" : "#96a9be");
        cmd.set("#NavBtnList.Style.Default.LabelStyle.RenderBold", listActive);

        // Bouton Creer
        cmd.set("#NavBtnCreate.Style.Default.Background", createActive ? "#3a5a7a" : "#1f2d3f");
        cmd.set("#NavBtnCreate.Style.Default.LabelStyle.TextColor", createActive ? "#ffffff" : "#96a9be");
        cmd.set("#NavBtnCreate.Style.Default.LabelStyle.RenderBold", createActive);
    }

    /**
     * Met à jour l'apparence du bouton bypass selon l'état actuel.
     */
    private void updateBypassButton(UICommandBuilder cmd) {
        Player player = getPlayer();
        if (player == null) return;

        boolean bypassing = RegionPermissionChecker.isBypassing(player.getUuid());
        if (bypassing) {
            cmd.set("#BtnToggleBypass.Text", "BYPASS: ON");
            cmd.set("#BtnToggleBypass.Style.Default.Background", "#1a3a1a");
            cmd.set("#BtnToggleBypass.Style.Default.LabelStyle.TextColor", "#5adf5a");
            cmd.set("#BtnToggleBypass.Style.Hovered.Background", "#2a4a2a");
            cmd.set("#BtnToggleBypass.Style.Hovered.LabelStyle.TextColor", "#7aff7a");
        } else {
            cmd.set("#BtnToggleBypass.Text", "BYPASS: OFF");
            cmd.set("#BtnToggleBypass.Style.Default.Background", "#3a1a1a");
            cmd.set("#BtnToggleBypass.Style.Default.LabelStyle.TextColor", "#ff6666");
            cmd.set("#BtnToggleBypass.Style.Hovered.Background", "#4a2a2a");
            cmd.set("#BtnToggleBypass.Style.Hovered.LabelStyle.TextColor", "#ff8888");
        }
    }

    // ===========================================
    // CHARGEMENT DES DONNEES
    // ===========================================

    private void loadHomeData(UICommandBuilder cmd) {
        List<RegionImpl> allRegions = regionService.getRegionsByWorld(worldName);
        Player player = getPlayer();
        UUID playerUuid = player != null ? player.getUuid() : null;

        int totalRegions = 0;
        int myRegions = 0;
        long totalBlocks = 0;
        int cuboidCount = 0;
        int cylinderCount = 0;

        for (RegionImpl region : allRegions) {
            // Ne pas compter la région globale dans les stats
            if (RegionService.isGlobalRegion(region)) continue;

            totalRegions++;
            totalBlocks += region.getShape().getVolume();
            if (playerUuid != null && region.isOwner(playerUuid)) {
                myRegions++;
            }
            if (region.getShape() instanceof CylinderShape) {
                cylinderCount++;
            } else {
                cuboidCount++;
            }
        }

        cmd.set("#StatRegionCount.Text", String.valueOf(totalRegions));
        cmd.set("#StatMyRegionCount.Text", String.valueOf(myRegions));
        cmd.set("#StatProtectedBlocks.Text", formatNumber(totalBlocks));

        // Région globale - auto-créer si elle n'existe pas pour ce monde
        Optional<RegionImpl> globalOpt = regionService.getGlobalRegion(worldName);
        if (globalOpt.isPresent()) {
            RegionImpl global = globalOpt.get();
            cmd.set("#GlobalRegionStatus.Text", "Active");
            cmd.set("#GlobalRegionStatus.Style.TextColor", "#00ff7f");

            // Compter les flags configurés
            int flagCount = 0;
            for (RegionFlag flag : RegionFlag.values()) {
                if (global.getRawFlag(flag) != null) flagCount++;
            }
            cmd.set("#GlobalRegionInfo.Text", flagCount + " flag(s) configure(s)");
            cmd.set("#BtnCreateGlobal.Visible", false);
            cmd.set("#BtnEditGlobal.Visible", true);
        } else {
            // Région globale n'existe pas pour ce monde - la créer automatiquement
            cmd.set("#GlobalRegionStatus.Text", "Creation...");
            cmd.set("#GlobalRegionStatus.Style.TextColor", "#ffa500");
            cmd.set("#GlobalRegionInfo.Text", "Initialisation en cours...");
            cmd.set("#BtnCreateGlobal.Visible", false);
            cmd.set("#BtnEditGlobal.Visible", false);

            // Créer la région globale en async et rafraîchir
            regionService.getOrCreateGlobalRegion(worldName)
                .thenAccept(region -> {
                    if (player != null) {
                        player.sendMessage(ColorUtil.parse("&aRegion globale creee pour '" + worldName + "'!"));
                    }
                    refreshHomePage();
                })
                .exceptionally(e -> {
                    if (player != null) {
                        player.sendMessage(ColorUtil.parse("&cErreur creation region globale: " + e.getMessage()));
                    }
                    return null;
                });
        }
    }

    private void loadListData(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#RegionsList");

        // Filtrer les régions globales - elles sont gérées séparément dans l'accueil
        List<RegionImpl> regions = regionService.getRegionsByWorld(worldName).stream()
            .filter(r -> !RegionService.isGlobalRegion(r))
            .toList();
        Player player = getPlayer();
        UUID playerUuid = player != null ? player.getUuid() : null;

        if (regions.isEmpty()) {
            cmd.appendInline("#RegionsList",
                "Label { Text: \"Aucune region dans ce monde\"; Anchor: (Height: 40); Style: (FontSize: 13, TextColor: #808080, HorizontalAlignment: Center, VerticalAlignment: Center); }");
        } else {
            // Trier par priorité décroissante
            List<RegionImpl> sorted = new ArrayList<>(regions);
            sorted.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

            for (int i = 0; i < sorted.size(); i++) {
                RegionImpl region = sorted.get(i);
                boolean isOwner = playerUuid != null && region.isOwner(playerUuid);
                boolean isMember = playerUuid != null && region.isMember(playerUuid);
                String rowId = "RR" + i;
                String bgColor = isOwner ? "#1a2a3a" : "#151d28";
                RegionShape shape = region.getShape();

                boolean isCylinder = shape instanceof CylinderShape;
                String shapeLabel = isCylinder ? "CYL" : "CUBE";
                String shapeBg = isCylinder ? "#2a4a5a" : "#2a5a2a";

                // Row avec layout Left: [Indicateur owner | Infos | Badge forme | Priorité]
                String rowCode = String.format(
                    "Button #%s { Anchor: (Height: 52, Bottom: 4); Background: (Color: %s); Padding: (Horizontal: 10, Vertical: 6); LayoutMode: Left; " +
                    // Barre colorée gauche (indicateur owner/member)
                    "Group { Anchor: (Width: 3, Height: 40); Background: (Color: %s); } " +
                    // Spacer
                    "Group { Anchor: (Width: 10); } " +
                    // Bloc info (nom + volume)
                    "Group { FlexWeight: 1; LayoutMode: Top; " +
                        "Label #N { Anchor: (Height: 22); Style: (FontSize: 13, TextColor: #e0e0e0, VerticalAlignment: Center, RenderBold: true); } " +
                        "Label #V { Anchor: (Height: 18); Style: (FontSize: 10, TextColor: #707a88, VerticalAlignment: Center); } " +
                    "} " +
                    // Badge forme
                    "Group { Anchor: (Width: 50, Height: 22); Background: (Color: %s); " +
                        "Label #S { Style: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center, RenderBold: true); } " +
                    "} " +
                    // Spacer
                    "Group { Anchor: (Width: 8); } " +
                    // Priorité
                    "Group { Anchor: (Width: 45); LayoutMode: Top; " +
                        "Label { Text: \"PRI\"; Anchor: (Height: 16); Style: (FontSize: 9, TextColor: #606a78, HorizontalAlignment: Center); } " +
                        "Label #P { Anchor: (Height: 24); Style: (FontSize: 14, TextColor: #ffd700, HorizontalAlignment: Center, VerticalAlignment: Center, RenderBold: true); } " +
                    "} " +
                    "}",
                    rowId, bgColor,
                    isOwner ? "#ffd700" : (isMember ? "#00bfff" : "#3a3a4a"),
                    shapeBg
                );

                cmd.appendInline("#RegionsList", rowCode);

                // Définir les textes via cmd.set() APRÈS l'ajout
                String safeName = sanitize(region.getName());
                if (safeName.isEmpty()) safeName = "Region " + i;
                cmd.set("#" + rowId + " #N.Text", safeName);

                // Info volume + membres
                long volume = shape.getVolume();
                int ownerCount = region.getOwners().size();
                int memberCount = region.getMembers().size();
                cmd.set("#" + rowId + " #V.Text", formatNumber(volume) + " blocs | " + ownerCount + " own. " + memberCount + " mbr.");

                cmd.set("#" + rowId + " #S.Text", shapeLabel);
                cmd.set("#" + rowId + " #P.Text", String.valueOf(region.getPriority()));

                event.addEventBinding(CustomUIEventBindingType.Activating, "#" + rowId,
                    EventData.of("SelectRegion", region.getName()), false);
            }
        }
    }

    private void loadCreateData(UICommandBuilder cmd) {
        // Toggle forme - mettre à jour l'apparence des boutons
        boolean isCuboid = "cuboid".equals(createShapeType);
        cmd.set("#BtnShapeCuboid.Background.Color", isCuboid ? "#2a5a2a" : "#1f2d3f");
        cmd.set("#LblShapeCuboid.Style.TextColor", isCuboid ? "#ffffff" : "#96a9be");
        cmd.set("#LblShapeCuboid.Style.RenderBold", isCuboid);
        cmd.set("#BtnShapeCylinder.Background.Color", isCuboid ? "#1f2d3f" : "#2a5a5a");
        cmd.set("#LblShapeCylinder.Style.TextColor", isCuboid ? "#96a9be" : "#ffffff");
        cmd.set("#LblShapeCylinder.Style.RenderBold", !isCuboid);

        // Afficher/masquer les options selon la forme
        cmd.set("#CylinderOptions.Visible", !isCuboid);
        cmd.set("#SelectionInfo.Visible", isCuboid);

        // Mettre à jour le label de sélection
        if (isCuboid) {
            cmd.set("#SelectionLabel.Text", "Selection actuelle (cuboid)");
            Player player = getPlayer();
            if (player != null && SelectionHelper.hasValidSelection(player)) {
                Optional<BoundingBox> bounds = SelectionHelper.getSelectionAsBoundingBox(player);
                if (bounds.isPresent()) {
                    BoundingBox box = bounds.get();
                    cmd.set("#SelectionCoords.Text", String.format("Min: (%d, %d, %d) - Max: (%d, %d, %d)",
                        box.getMinX(), box.getMinY(), box.getMinZ(),
                        box.getMaxX(), box.getMaxY(), box.getMaxZ()));
                    cmd.set("#SelectionVolume.Text", "Volume: " + formatNumber(box.getVolume()) + " blocs");
                    return;
                }
            }
            cmd.set("#SelectionCoords.Text", "Aucune selection - utilisez /pos1 et /pos2");
            cmd.set("#SelectionVolume.Text", "");
        } else {
            // Mode cylindre - le cylindre sera créé à la position du joueur
            Player player = getPlayer();
            if (player != null) {
                var transform = player.getTransformComponent();
                var pos = transform.getPosition();
                cmd.set("#SelectionLabel.Text", "Position du cylindre");
                cmd.set("#SelectionCoords.Text", String.format("Centre: (%d, %d, %d) - votre position",
                    (int) Math.floor(pos.getX()),
                    (int) Math.floor(pos.getY()),
                    (int) Math.floor(pos.getZ())));
                cmd.set("#SelectionVolume.Text", "Le cylindre sera cree a vos pieds");
            }
        }
    }

    private void loadEditorData(UICommandBuilder cmd, UIEventBuilder event) {
        if (selectedRegionName == null) {
            plugin.log(java.util.logging.Level.WARNING, "loadEditorData: selectedRegionName is null");
            return;
        }

        plugin.log(java.util.logging.Level.INFO, "loadEditorData: Loading region '" + selectedRegionName + "'");

        Optional<RegionImpl> regionOpt = regionService.getRegion(worldName, selectedRegionName).join();
        if (regionOpt.isEmpty()) {
            plugin.log(java.util.logging.Level.WARNING, "loadEditorData: Region not found");
            return;
        }

        RegionImpl region = regionOpt.get();
        RegionShape shape = region.getShape();
        boolean isGlobal = RegionService.isGlobalRegion(region);
        plugin.log(java.util.logging.Level.INFO, "loadEditorData: Region found, isGlobal=" + isGlobal + ", shape=" + shape.getClass().getSimpleName());

        // Pour la région globale, on fait un traitement MINIMALISTE pour éviter les crashs client
        if (isGlobal) {
            plugin.log(java.util.logging.Level.INFO, "loadEditorData: GLOBAL region - minimal update only");
            // NE RIEN FAIRE - laisser les valeurs par défaut du fichier UI
            // Le crash semble venir de certaines modifications UI
            plugin.log(java.util.logging.Level.INFO, "loadEditorData: COMPLETED (global - no changes)");
            return;
        }

        try {
            // Infos de base
            String displayName = sanitize(region.getName());
            cmd.set("#EditorRegionName.Text", displayName);

            // Affichage selon le type de forme
            String volumeInfo;
            if (shape instanceof CylinderShape cyl) {
                volumeInfo = "Volume: " + formatNumber(shape.getVolume()) + " blocs | cylindre";
            } else {
                volumeInfo = "Volume: " + formatNumber(shape.getVolume()) + " blocs | cuboid";
            }

            cmd.set("#EditorVolume.Text", volumeInfo);

            int ownerCount = region.getOwners().size();
            int memberCount = region.getMembers().size();
            cmd.set("#EditorOwners.Text", "Owners: " + ownerCount);
            cmd.set("#EditorMembers.Text", "Membres: " + memberCount);

            int priority = region.getPriority();
            cmd.set("#EditorPriority.Text", "Priorite: " + priority);
            cmd.set("#InputNewPriority.Value", priority);

            // Mettre à jour les valeurs selon le type de forme - exactement comme Priorité
            if (shape instanceof CylinderShape cyl) {
                int radius = cyl.getRadius();
                int height = cyl.getHeight();

                cmd.set("#LabelShapeType.Text", "Forme: Cylindre");
                cmd.set("#EditorCylinderRadius.Value", radius);
                cmd.set("#EditorCylinderHeight.Value", height);

                plugin.log(java.util.logging.Level.INFO, "Cylinder shape loaded: radius=" + radius + ", height=" + height);

            } else if (shape instanceof CuboidShape cuboid) {
                BoundingBox bounds = cuboid.getBounds();
                String boundsText = String.format("Min: (%d,%d,%d) - Max: (%d,%d,%d)",
                    bounds.getMinX(), bounds.getMinY(), bounds.getMinZ(),
                    bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ());

                cmd.set("#LabelShapeType.Text", "Forme: Cuboid - " + boundsText);

                plugin.log(java.util.logging.Level.INFO, "Cuboid shape loaded");
            }

        } catch (Exception e) {
            plugin.log(java.util.logging.Level.SEVERE, "loadEditorData: Error setting basic info: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Gestion du bouton supprimer
        cmd.set("#BtnDeleteRegion.Visible", !confirmDelete);
        cmd.set("#DeleteConfirmButtons.Visible", confirmDelete);

        // Flags actifs - VERSION ENRICHIE avec overrides
        try {
            cmd.clear("#ActiveFlagsList");

            // 1. FLAGS DE BASE DE LA REGION
            Map<RegionFlag, Object> flags = region.getFlags();
            if (!flags.isEmpty()) {
                cmd.appendInline("#ActiveFlagsList",
                    "Label { Text: \"FLAGS DE BASE\"; Anchor: (Height: 26, Bottom: 4); Style: (FontSize: 11, TextColor: #ffd700, RenderBold: true, RenderUppercase: true); }");

                for (Map.Entry<RegionFlag, Object> entry : flags.entrySet()) {
                    displayFlagRow(cmd, entry.getKey(), entry.getValue(), null);
                }
            }

            // 2. OVERRIDES DE GROUPES
            Map<String, Map<RegionFlag, Object>> groupOverrides = region.getOverrideCache().getAllGroupOverrides();
            if (!groupOverrides.isEmpty()) {
                cmd.appendInline("#ActiveFlagsList",
                    "Label { Text: \"OVERRIDES GROUPES\"; Anchor: (Height: 26, Bottom: 4, Top: 8); Style: (FontSize: 11, TextColor: #d77dff, RenderBold: true, RenderUppercase: true); }");

                for (Map.Entry<String, Map<RegionFlag, Object>> groupEntry : groupOverrides.entrySet()) {
                    String groupName = groupEntry.getKey();
                    for (Map.Entry<RegionFlag, Object> flagEntry : groupEntry.getValue().entrySet()) {
                        displayFlagRow(cmd, flagEntry.getKey(), flagEntry.getValue(), "G: " + groupName);
                    }
                }
            }

            // 3. OVERRIDES DE JOUEURS
            Map<UUID, Map<RegionFlag, Object>> playerOverrides = region.getOverrideCache().getAllPlayerOverrides();
            if (!playerOverrides.isEmpty()) {
                cmd.appendInline("#ActiveFlagsList",
                    "Label { Text: \"OVERRIDES JOUEURS\"; Anchor: (Height: 26, Bottom: 4, Top: 8); Style: (FontSize: 11, TextColor: #6ab4ff, RenderBold: true, RenderUppercase: true); }");

                for (Map.Entry<UUID, Map<RegionFlag, Object>> playerEntry : playerOverrides.entrySet()) {
                    String playerName = getPlayerName(playerEntry.getKey());
                    for (Map.Entry<RegionFlag, Object> flagEntry : playerEntry.getValue().entrySet()) {
                        displayFlagRow(cmd, flagEntry.getKey(), flagEntry.getValue(), "P: " + playerName);
                    }
                }
            }

            // Si aucun flag configuré
            if (flags.isEmpty() && groupOverrides.isEmpty() && playerOverrides.isEmpty()) {
                cmd.appendInline("#ActiveFlagsList",
                    "Label { Text: \"Aucun flag configure\"; Anchor: (Height: 30); Style: (FontSize: 12, TextColor: #808080, HorizontalAlignment: Center); }");
            }
        } catch (Exception e) {
            plugin.log(java.util.logging.Level.SEVERE, "loadEditorData: Error loading flags: " + e.getMessage());
            e.printStackTrace();
        }

        plugin.log(java.util.logging.Level.INFO, "loadEditorData: COMPLETED successfully");
    }

    private void loadFlagsData(UICommandBuilder cmd, UIEventBuilder event) {
        if (selectedRegionName == null) return;

        Optional<RegionImpl> regionOpt = regionService.getRegion(worldName, selectedRegionName).join();
        if (regionOpt.isEmpty()) return;

        RegionImpl region = regionOpt.get();

        // Afficher un nom plus lisible pour la région globale
        String displayName = RegionService.isGlobalRegion(region) ? "Region Globale" : sanitize(region.getName());
        cmd.set("#FlagsTitle.Text", "Flags - " + displayName);

        // Mettre à jour le style des onglets
        updateFlagsTabStyle(cmd);

        // Afficher/cacher les contenus selon l'onglet actif
        cmd.set("#TabContentRegion.Visible", "region".equals(currentFlagsTab));
        cmd.set("#TabContentGroup.Visible", "group".equals(currentFlagsTab));
        cmd.set("#TabContentPlayer.Visible", "player".equals(currentFlagsTab));

        // Charger le contenu de l'onglet actif
        switch (currentFlagsTab) {
            case "region" -> loadRegionFlagsTab(cmd, event, region);
            case "group" -> loadGroupFlagsTab(cmd, event, region);
            case "player" -> loadPlayerFlagsTab(cmd, event, region);
        }
    }

    private void updateFlagsTabStyle(UICommandBuilder cmd) {
        // Onglet actif: fond bleu, texte blanc gras
        // Onglet inactif: fond sombre, texte gris
        String activeBg = "#3a5a7a";
        String inactiveBg = "#1f2d3f";
        String activeText = "#ffffff";
        String inactiveText = "#96a9be";

        cmd.set("#TabRegion.Style.Default.Background", "region".equals(currentFlagsTab) ? activeBg : inactiveBg);
        cmd.set("#TabGroup.Style.Default.Background", "group".equals(currentFlagsTab) ? activeBg : inactiveBg);
        cmd.set("#TabPlayer.Style.Default.Background", "player".equals(currentFlagsTab) ? activeBg : inactiveBg);
    }

    private void loadRegionFlagsTab(UICommandBuilder cmd, UIEventBuilder event, RegionImpl region) {
        Map<RegionFlag, Object> activeFlags = region.getFlags();
        cmd.clear("#FlagsList");

        int index = 0;
        for (RegionFlag flag : RegionFlag.values()) {
            // Obtenir le statut d'implémentation pour la couleur du nom
            RegionFlag.ImplementationStatus implStatus = flag.getStatus();
            String nameColor = implStatus.getColor(); // Vert/Jaune/Gris selon implémentation

            // Seulement les flags booleens sont cliquables
            if (flag.getType() != RegionFlag.FlagType.BOOLEAN) {
                // Pour les flags non-booleens (STRING, INTEGER), afficher un simple label
                Object value = activeFlags.get(flag);
                String valueStr = value != null ? sanitize(value.toString()) : "---";
                String labelCode = String.format(
                    "Label { Text: \"%s: %s\"; Anchor: (Height: 28); Style: (FontSize: 13, TextColor: %s); }",
                    formatFlagName(flag.getName()), valueStr, nameColor
                );
                cmd.appendInline("#FlagsList", labelCode);
                continue;
            }

            Object currentValue = activeFlags.get(flag);
            String rowId = "Flag" + index;

            String status;
            String bgColor;
            String statusColor;
            String nextAction;

            // 4 états: null (---) -> true (ON) -> members (MEMBRES) -> false (OFF TOUS) -> clear
            if (currentValue == null) {
                status = "---";
                bgColor = "#252d38";
                statusColor = "#606060";
                nextAction = "true";
            } else if (Boolean.TRUE.equals(currentValue)) {
                status = "ON";
                bgColor = "#1a3a1a";
                statusColor = "#5adf5a";
                nextAction = "members";
            } else if ("members".equals(currentValue)) {
                status = "MEMBRES";
                bgColor = "#2a2a1a";
                statusColor = "#dfdf5a";
                nextAction = "false";
            } else {
                // Boolean.FALSE - OFF pour tous
                status = "OFF";
                bgColor = "#3a1a1a";
                statusColor = "#df5a5a";
                nextAction = "clear";
            }

            // Nom du flag avec couleur selon implémentation + statut de la valeur
            String displayText = formatFlagName(flag.getName()) + ": " + status;

            String rowCode = String.format(
                "Button #%s { Anchor: (Height: 32, Bottom: 2); Background: (Color: %s); Padding: (Horizontal: 10); Label #Lbl { Style: (FontSize: 12, VerticalAlignment: Center); } }",
                rowId, bgColor
            );
            cmd.appendInline("#FlagsList", rowCode);
            cmd.set("#" + rowId + " #Lbl.Text", displayText);
            // Couleur du texte = couleur d'implémentation (vert/jaune/gris) pour le nom
            cmd.set("#" + rowId + " #Lbl.Style.TextColor", nameColor);

            // Event binding - SetFlag avec format "flagName:nextValue"
            event.addEventBinding(CustomUIEventBindingType.Activating, "#" + rowId,
                EventData.of("SetFlag", flag.getName() + ":" + nextAction), false);

            index++;
        }
    }

    private void loadGroupFlagsTab(UICommandBuilder cmd, UIEventBuilder event, RegionImpl region) {
        cmd.clear("#RankButtonsContainer");
        cmd.clear("#GroupFlagsList");

        // Charger les ranks disponibles depuis le PermissionService
        try {
            PermissionService permService = IslandiumPlugin.get().getServiceManager().getPermissionService();
            List<Rank> ranks = permService.getAllRanks().join();

            // Trier par priorité décroissante
            ranks.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

            int idx = 0;
            for (Rank rank : ranks) {
                String btnId = "RankBtn" + idx;
                boolean isSelected = rank.getName().equalsIgnoreCase(selectedRankName);
                String bgColor = isSelected ? "#2a5a2a" : "#1f2d3f";
                String textColor = isSelected ? "#ffffff" : "#96a9be";

                String btnCode = String.format(
                    "TextButton #%s { Anchor: (Height: 30, Right: 5); Padding: (Horizontal: 12); Text: \"%s\"; Style: TextButtonStyle(Default: (Background: %s, LabelStyle: (FontSize: 11, TextColor: %s, HorizontalAlignment: Center, VerticalAlignment: Center)), Hovered: (Background: #3a6a3a, LabelStyle: (FontSize: 11, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center))); }",
                    btnId, sanitize(rank.getDisplayName()), bgColor, textColor
                );
                cmd.appendInline("#RankButtonsContainer", btnCode);
                event.addEventBinding(CustomUIEventBindingType.Activating, "#" + btnId,
                    EventData.of("SelectRank", rank.getName()), false);
                idx++;
            }

            // Afficher les infos du groupe sélectionné
            if (selectedRankName != null) {
                cmd.set("#SelectedGroupInfo.Visible", true);
                cmd.set("#SelectedGroupName.Text", "Groupe: " + sanitize(selectedRankName));

                // Charger les flags de ce groupe
                loadGroupFlagsForRank(cmd, event, region, selectedRankName);
            } else {
                cmd.set("#SelectedGroupInfo.Visible", false);
                cmd.appendInline("#GroupFlagsList",
                    "Label { Text: \"Selectionnez un groupe ci dessus\"; Anchor: (Height: 40); Style: (FontSize: 13, TextColor: #808080, HorizontalAlignment: Center, VerticalAlignment: Center); }");
            }
        } catch (Exception e) {
            cmd.appendInline("#GroupFlagsList",
                "Label { Text: \"Erreur chargement des groupes\"; Anchor: (Height: 40); Style: (FontSize: 13, TextColor: #ff6666, HorizontalAlignment: Center); }");
        }
    }

    private void loadGroupFlagsForRank(UICommandBuilder cmd, UIEventBuilder event, RegionImpl region, String rankName) {
        Map<RegionFlag, Object> groupFlags = region.getOverrideCache().getGroupOverrides(rankName);

        int index = 0;
        for (RegionFlag flag : RegionFlag.values()) {
            if (flag.getType() != RegionFlag.FlagType.BOOLEAN) continue;

            // Obtenir le statut d'implémentation pour la couleur du nom
            RegionFlag.ImplementationStatus implStatus = flag.getStatus();
            String nameColor = implStatus.getColor();

            Object currentValue = groupFlags.get(flag);
            String rowId = "GFlag" + index;

            String status;
            String bgColor;
            String nextAction;

            // 4 états: null (pas d'override) -> true -> members -> false -> clear
            if (currentValue == null) {
                status = "---";
                bgColor = "#252d38";
                nextAction = "true";
            } else if (Boolean.TRUE.equals(currentValue)) {
                status = "ON";
                bgColor = "#1a3a1a";
                nextAction = "members";
            } else if ("members".equals(currentValue)) {
                status = "MEMBRES";
                bgColor = "#2a2a1a";
                nextAction = "false";
            } else {
                status = "OFF";
                bgColor = "#3a1a1a";
                nextAction = "clear";
            }

            String displayText = formatFlagName(flag.getName()) + ": " + status;

            String rowCode = String.format(
                "Button #%s { Anchor: (Height: 32, Bottom: 2); Background: (Color: %s); Padding: (Horizontal: 10); Label #Lbl { Style: (FontSize: 12, VerticalAlignment: Center); } }",
                rowId, bgColor
            );
            cmd.appendInline("#GroupFlagsList", rowCode);
            cmd.set("#" + rowId + " #Lbl.Text", displayText);
            cmd.set("#" + rowId + " #Lbl.Style.TextColor", nameColor);

            // Event binding - SetGroupFlag avec format "rankName:flagName:nextValue"
            event.addEventBinding(CustomUIEventBindingType.Activating, "#" + rowId,
                EventData.of("SetGroupFlag", rankName + ":" + flag.getName() + ":" + nextAction), false);

            index++;
        }
    }

    private void loadPlayerFlagsTab(UICommandBuilder cmd, UIEventBuilder event, RegionImpl region) {
        cmd.clear("#PlayerFlagsList");
        cmd.clear("#PlayerButtonsContainer");

        // Charger la liste des joueurs ayant des overrides
        Set<UUID> playersWithOverrides = region.getOverrideCache().getPlayersWithOverrides();

        if (!playersWithOverrides.isEmpty()) {
            int idx = 0;
            for (UUID playerUuid : playersWithOverrides) {
                String playerName = getPlayerName(playerUuid);
                String btnId = "PlayerBtn" + idx;
                boolean isSelected = playerUuid.equals(selectedPlayerUuid);
                String bgColor = isSelected ? "#2a5a2a" : "#1f2d3f";
                String textColor = isSelected ? "#ffffff" : "#96a9be";

                String btnCode = String.format(
                    "TextButton #%s { Anchor: (Height: 30, Right: 5); Padding: (Horizontal: 12); Text: \"%s\"; Style: TextButtonStyle(Default: (Background: %s, LabelStyle: (FontSize: 11, TextColor: %s, HorizontalAlignment: Center, VerticalAlignment: Center)), Hovered: (Background: #3a6a3a, LabelStyle: (FontSize: 11, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center))); }",
                    btnId, sanitize(playerName), bgColor, textColor
                );
                cmd.appendInline("#PlayerButtonsContainer", btnCode);
                event.addEventBinding(CustomUIEventBindingType.Activating, "#" + btnId,
                    EventData.of("SelectPlayer", playerUuid.toString()), false);
                idx++;
            }
        } else {
            cmd.appendInline("#PlayerButtonsContainer",
                "Label { Text: \"Aucun joueur avec overrides\"; Anchor: (Height: 30); Style: (FontSize: 11, TextColor: #606060, HorizontalAlignment: Center, VerticalAlignment: Center); }");
        }

        // Afficher les infos du joueur sélectionné
        if (selectedPlayerUuid != null && selectedPlayerName != null) {
            cmd.set("#SelectedPlayerInfo.Visible", true);
            cmd.set("#SelectedPlayerName.Text", "Joueur: " + sanitize(selectedPlayerName));

            // Charger les flags de ce joueur
            loadPlayerFlagsForPlayer(cmd, event, region, selectedPlayerUuid);
        } else {
            cmd.set("#SelectedPlayerInfo.Visible", false);
            cmd.appendInline("#PlayerFlagsList",
                "Label { Text: \"Selectionnez un joueur ci dessus\"; Anchor: (Height: 40); Style: (FontSize: 13, TextColor: #808080, HorizontalAlignment: Center, VerticalAlignment: Center); }");
        }
    }

    private void loadPlayerFlagsForPlayer(UICommandBuilder cmd, UIEventBuilder event, RegionImpl region, UUID playerUuid) {
        Map<RegionFlag, Object> playerFlags = region.getOverrideCache().getPlayerOverrides(playerUuid);

        int index = 0;
        for (RegionFlag flag : RegionFlag.values()) {
            if (flag.getType() != RegionFlag.FlagType.BOOLEAN) continue;

            // Obtenir le statut d'implémentation pour la couleur du nom
            RegionFlag.ImplementationStatus implStatus = flag.getStatus();
            String nameColor = implStatus.getColor();

            Object currentValue = playerFlags.get(flag);
            String rowId = "PFlag" + index;

            String status;
            String bgColor;
            String nextAction;

            // 4 états: null (pas d'override) -> true -> members -> false -> clear
            if (currentValue == null) {
                status = "---";
                bgColor = "#252d38";
                nextAction = "true";
            } else if (Boolean.TRUE.equals(currentValue)) {
                status = "ON";
                bgColor = "#1a3a1a";
                nextAction = "members";
            } else if ("members".equals(currentValue)) {
                status = "MEMBRES";
                bgColor = "#2a2a1a";
                nextAction = "false";
            } else {
                status = "OFF";
                bgColor = "#3a1a1a";
                nextAction = "clear";
            }

            String displayText = formatFlagName(flag.getName()) + ": " + status;

            String rowCode = String.format(
                "Button #%s { Anchor: (Height: 32, Bottom: 2); Background: (Color: %s); Padding: (Horizontal: 10); Label #Lbl { Style: (FontSize: 12, VerticalAlignment: Center); } }",
                rowId, bgColor
            );
            cmd.appendInline("#PlayerFlagsList", rowCode);
            cmd.set("#" + rowId + " #Lbl.Text", displayText);
            cmd.set("#" + rowId + " #Lbl.Style.TextColor", nameColor);

            // Event binding - SetPlayerFlag avec format "playerUuid:flagName:nextValue"
            event.addEventBinding(CustomUIEventBindingType.Activating, "#" + rowId,
                EventData.of("SetPlayerFlag", playerUuid.toString() + ":" + flag.getName() + ":" + nextAction), false);

            index++;
        }
    }

    private void loadMembersData(UICommandBuilder cmd, UIEventBuilder event) {
        if (selectedRegionName == null) return;

        Optional<RegionImpl> regionOpt = regionService.getRegion(worldName, selectedRegionName).join();
        if (regionOpt.isEmpty()) return;

        RegionImpl region = regionOpt.get();

        cmd.set("#MembersTitle.Text", "Membres - " + sanitize(region.getName()));

        // Proprietaires
        cmd.clear("#OwnersList");
        if (region.getOwners().isEmpty()) {
            cmd.appendInline("#OwnersList",
                "Label { Text: \"Aucun proprietaire\"; Anchor: (Height: 30); Style: (FontSize: 12, TextColor: #808080, HorizontalAlignment: Center); }");
        } else {
            int idx = 0;
            for (UUID ownerId : region.getOwners()) {
                String name = getPlayerName(ownerId);
                String rowId = "Owner" + idx++;

                // IMPORTANT: Utiliser Anchor: (Height: X, Bottom: Y) et non Margin: (Bottom: Y)
                String rowCode = String.format(
                    "Group #%s { Anchor: (Height: 36, Bottom: 4); LayoutMode: Left; Background: (Color: #1a2a1a); Padding: (Horizontal: 10, Vertical: 4); " +
                    "Label { FlexWeight: 1; Text: \"%s\"; Style: (FontSize: 13, TextColor: %s, VerticalAlignment: Center); } " +
                    "TextButton #%sRemove { Anchor: (Width: 28, Height: 28); Text: \"X\"; Style: TextButtonStyle(Default: (Background: (Color: #4a2a2a), LabelStyle: (FontSize: 12, TextColor: #ff6666, HorizontalAlignment: Center, VerticalAlignment: Center)), Hovered: (Background: (Color: #6a3a3a), LabelStyle: (FontSize: 12, TextColor: #ff8888, HorizontalAlignment: Center, VerticalAlignment: Center))); } }",
                    rowId, sanitize(name), COLOR_GOLD, rowId
                );

                cmd.appendInline("#OwnersList", rowCode);
                event.addEventBinding(CustomUIEventBindingType.Activating, "#" + rowId + "Remove",
                    EventData.of("RemoveOwner", ownerId.toString()), false);
            }
        }

        // Membres
        cmd.clear("#MembersList");
        if (region.getMembers().isEmpty()) {
            cmd.appendInline("#MembersList",
                "Label { Text: \"Aucun membre\"; Anchor: (Height: 30); Style: (FontSize: 12, TextColor: #808080, HorizontalAlignment: Center); }");
        } else {
            int idx = 0;
            for (UUID memberId : region.getMembers()) {
                String name = getPlayerName(memberId);
                String rowId = "Member" + idx++;

                // IMPORTANT: Utiliser Anchor: (Height: X, Bottom: Y) et non Margin: (Bottom: Y)
                String rowCode = String.format(
                    "Group #%s { Anchor: (Height: 36, Bottom: 4); LayoutMode: Left; Background: (Color: %s); Padding: (Horizontal: 10, Vertical: 4); " +
                    "Label { FlexWeight: 1; Text: \"%s\"; Style: (FontSize: 13, TextColor: #e0e0e0, VerticalAlignment: Center); } " +
                    "TextButton #%sRemove { Anchor: (Width: 28, Height: 28); Text: \"X\"; Style: TextButtonStyle(Default: (Background: (Color: #4a2a2a), LabelStyle: (FontSize: 12, TextColor: #ff6666, HorizontalAlignment: Center, VerticalAlignment: Center)), Hovered: (Background: (Color: #6a3a3a), LabelStyle: (FontSize: 12, TextColor: #ff8888, HorizontalAlignment: Center, VerticalAlignment: Center))); } }",
                    rowId, COLOR_BG_PANEL, sanitize(name), rowId
                );

                cmd.appendInline("#MembersList", rowCode);
                event.addEventBinding(CustomUIEventBindingType.Activating, "#" + rowId + "Remove",
                    EventData.of("RemoveMember", memberId.toString()), false);
            }
        }
    }

    // ===========================================
    // GESTION DES EVENTS
    // ===========================================

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        this.currentRef = ref;
        this.currentStore = store;

        Player player = getPlayer();
        if (player == null) return;

        // Debug log
        plugin.log(java.util.logging.Level.INFO, "handleDataEvent: action=" + data.action + ", navTo=" + data.navTo + ", regionName=" + data.regionName);

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder event = new UIEventBuilder();

        // Navigation
        if (data.navTo != null) {
            confirmDelete = false;
            // Les pages avec appendInline (list, flags, members, editor) nécessitent rebuild()
            if ("list".equals(data.navTo) || "flags".equals(data.navTo) || "members".equals(data.navTo) || "editor".equals(data.navTo)) {
                currentPage = data.navTo;
                rebuild();
            } else {
                showPage(data.navTo, cmd, event);
                sendUpdate(cmd, event, false);
            }
            return;
        }

        // Selection de region
        if (data.selectRegion != null) {
            selectedRegionName = data.selectRegion;
            confirmDelete = false;
            // Editor utilise appendInline, donc rebuild()
            currentPage = "editor";
            rebuild();
            return;
        }

        // Onglets flags
        if (data.flagsTab != null) {
            currentFlagsTab = data.flagsTab;
            // Flags utilise appendInline, donc rebuild()
            currentPage = "flags";
            rebuild();
            return;
        }

        // Sélection de rank (onglet groupe)
        if (data.selectRank != null) {
            selectedRankName = data.selectRank;
            // Flags utilise appendInline, donc rebuild()
            currentPage = "flags";
            rebuild();
            return;
        }

        // Sélection de joueur (onglet joueur)
        if (data.selectPlayer != null) {
            try {
                UUID uuid = UUID.fromString(data.selectPlayer);
                selectedPlayerUuid = uuid;
                selectedPlayerName = getPlayerName(uuid);
            } catch (IllegalArgumentException e) {
                plugin.log(java.util.logging.Level.WARNING, "Invalid UUID for player selection: " + data.selectPlayer);
            }
            // Flags utilise appendInline, donc rebuild()
            currentPage = "flags";
            rebuild();
            return;
        }

        // Gestion des flags de région
        if (data.setFlag != null) {
            handleSetFlag(data.setFlag, player);
            return;
        }

        // Gestion des flags de groupe
        if (data.setGroupFlag != null) {
            handleSetGroupFlag(data.setGroupFlag, player);
            return;
        }

        // Gestion des flags de joueur
        if (data.setPlayerFlag != null) {
            handleSetPlayerFlag(data.setPlayerFlag, player);
            return;
        }

        // Suppression membre/owner
        if (data.removeMember != null) {
            handleRemoveMember(data.removeMember, player, false);
            // Members utilise appendInline, donc rebuild()
            currentPage = "members";
            rebuild();
            return;
        }

        if (data.removeOwner != null) {
            handleRemoveMember(data.removeOwner, player, true);
            // Members utilise appendInline, donc rebuild()
            currentPage = "members";
            rebuild();
            return;
        }

        // Actions
        if (data.action == null) return;

        switch (data.action) {
            case "close" -> close();

            case "toggleBypass" -> {
                boolean nowBypassing = RegionPermissionChecker.toggleBypass(player.getUuid());
                if (nowBypassing) {
                    player.sendMessage(ColorUtil.parse("&a&lBYPASS ACTIVE &7- Vous ignorez toutes les protections de regions."));
                } else {
                    player.sendMessage(ColorUtil.parse("&c&lBYPASS DESACTIVE &7- Les protections de regions sont actives."));
                }
                // Mettre à jour le bouton sans changer de page
                updateBypassButton(cmd);
                sendUpdate(cmd, event, false);
            }

            case "teleport" -> handleTeleport(player);

            case "showZone" -> handleShowZone(player);

            case "deleteRegion" -> {
                confirmDelete = true;
                // Editor utilise appendInline, donc rebuild()
                currentPage = "editor";
                rebuild();
            }

            case "confirmDelete" -> {
                handleDeleteRegion(player);
                confirmDelete = false;
                selectedRegionName = null;
                // List utilise appendInline, donc rebuild()
                currentPage = "list";
                rebuild();
            }

            case "cancelDelete" -> {
                confirmDelete = false;
                // Editor utilise appendInline, donc rebuild()
                currentPage = "editor";
                rebuild();
            }

            case "createRegion" -> {
                plugin.log(java.util.logging.Level.INFO, "createRegion action triggered");
                plugin.log(java.util.logging.Level.INFO, "regionName: " + data.regionName);
                plugin.log(java.util.logging.Level.INFO, "regionPriority: " + data.regionPriority);
                handleCreateRegion(data, player);
                // IMPORTANT: rebuild() car appendInline ne fonctionne pas dans sendUpdate
                currentPage = "list";
                rebuild();
            }

            case "setShapeCuboid" -> {
                createShapeType = "cuboid";
                showPage("create", cmd, event);
                sendUpdate(cmd, event, false);
            }

            case "setShapeCylinder" -> {
                createShapeType = "cylinder";
                showPage("create", cmd, event);
                sendUpdate(cmd, event, false);
            }

            case "createGlobal" -> {
                // La région globale devrait être auto-créée au démarrage
                // Mais si elle n'existe pas, la créer en async sans bloquer
                handleCreateGlobalAsync(player);
                // Ne pas appeler showPage ici, le callback async le fera
            }

            case "editGlobal" -> {
                // Sélectionner la région globale et aller directement aux flags
                // (la page editor cause un crash client pour la région globale)
                selectedRegionName = RegionService.GLOBAL_REGION_NAME;
                // Flags utilise appendInline, donc rebuild()
                currentPage = "flags";
                rebuild();
            }

            case "addMember" -> {
                handleAddMember(data, player, false);
                // Members utilise appendInline, donc rebuild()
                currentPage = "members";
                rebuild();
            }

            case "addOwner" -> {
                handleAddMember(data, player, true);
                // Members utilise appendInline, donc rebuild()
                currentPage = "members";
                rebuild();
            }

            case "searchPlayer" -> {
                handleSearchPlayer(data.playerSearch, player);
                // Flags utilise appendInline, donc rebuild()
                currentPage = "flags";
                rebuild();
            }

            case "clearPlayerSelection" -> {
                selectedPlayerUuid = null;
                selectedPlayerName = null;
                // Flags utilise appendInline, donc rebuild()
                currentPage = "flags";
                rebuild();
            }

            case "changePriority" -> {
                handleChangePriority(data, player);
                // Editor utilise appendInline, donc rebuild()
                currentPage = "editor";
                rebuild();
            }

            case "updateFromSelection" -> {
                handleUpdateFromSelection(player);
                // Editor utilise appendInline, donc rebuild()
                currentPage = "editor";
                rebuild();
            }

            case "updateCylinder" -> {
                plugin.log(java.util.logging.Level.INFO, "[DEBUG] updateCylinder action triggered!");
                handleUpdateCylinder(data, player);
                // Editor utilise appendInline, donc rebuild()
                currentPage = "editor";
                rebuild();
            }
        }
    }

    // ===========================================
    // HANDLERS
    // ===========================================

    private void handleTeleport(Player player) {
        if (selectedRegionName == null) return;

        Optional<RegionImpl> regionOpt = regionService.getRegion(worldName, selectedRegionName).join();
        if (regionOpt.isEmpty()) return;

        RegionImpl region = regionOpt.get();

        // Pas de téléportation pour la région globale
        if (RegionService.isGlobalRegion(region)) {
            player.sendMessage(ColorUtil.parse("&cImpossible de se teleporter a la region globale."));
            return;
        }

        RegionShape shape = region.getShape();

        // Utiliser le centre de la forme
        double centerX = shape.getCenterX() + 0.5;
        double centerY;
        double centerZ = shape.getCenterZ() + 0.5;

        if (shape instanceof CylinderShape cyl) {
            // Pour un cylindre, se tp au centre en haut du cylindre
            centerY = cyl.getCenterY() + cyl.getHeight() + 1;
        } else {
            // Pour un cuboid, se tp au-dessus du centre
            centerY = shape.getEnclosingBounds().getMaxY() + 1;
        }

        try {
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform != null) {
                    Vector3d targetPos = new Vector3d(centerX, centerY, centerZ);
                    Vector3f currentRotation = transform.getRotation().clone();
                    Teleport teleport = new Teleport(targetPos, currentRotation);
                    store.addComponent(ref, Teleport.getComponentType(), teleport);
                    player.sendMessage(ColorUtil.parse("&aTeleporte au centre de '" + region.getName() + "'"));
                    return;
                }
            }
            player.sendMessage(ColorUtil.parse("&cImpossible de teleporter."));
        } catch (Exception e) {
            player.sendMessage(ColorUtil.parse("&cErreur lors de la teleportation"));
        }
    }

    private void handleShowZone(Player player) {
        if (selectedRegionName == null) return;

        Optional<RegionImpl> regionOpt = regionService.getRegion(worldName, selectedRegionName).join();
        if (regionOpt.isEmpty()) return;

        RegionImpl region = regionOpt.get();

        // Vérifier si c'est une région globale (ne peut pas être visualisée)
        if (RegionService.isGlobalRegion(region)) {
            player.sendMessage(ColorUtil.parse("&cLa region globale ne peut pas etre visualisee (trop grande)."));
            return;
        }

        // Utiliser le service de visualisation
        RegionVisualizationService vizService = plugin.getVisualizationService();
        boolean nowActive = vizService.toggleVisualization(player, region);

        if (nowActive) {
            player.sendMessage(ColorUtil.parse("&aVisualisation de '" + region.getName() + "' activee (5 min)."));
            player.sendMessage(ColorUtil.parse("&7Forme: " + region.getShape().getShapeType()));
        } else {
            player.sendMessage(ColorUtil.parse("&7Visualisation desactivee."));
        }
    }

    private void handleDeleteRegion(Player player) {
        if (selectedRegionName == null) return;
        regionService.deleteRegion(worldName, selectedRegionName).join();
        player.sendMessage(ColorUtil.parse("&aRegion '" + selectedRegionName + "' supprimee."));
    }

    private void handleCreateRegion(PageData data, Player player) {
        String name = data.regionName;
        if (name == null || name.isBlank()) {
            player.sendMessage(ColorUtil.parse("&cVeuillez entrer un nom pour la region."));
            return;
        }

        if (regionService.getRegion(worldName, name).join().isPresent()) {
            player.sendMessage(ColorUtil.parse("&cUne region avec ce nom existe deja."));
            return;
        }

        try {
            RegionShape shape;

            if ("cylinder".equals(createShapeType)) {
                // Création d'un cylindre
                var transform = player.getTransformComponent();
                var pos = transform.getPosition();
                int centerX = (int) Math.floor(pos.getX());
                int centerY = (int) Math.floor(pos.getY());
                int centerZ = (int) Math.floor(pos.getZ());

                // Les valeurs sont maintenant des Integer (pas des strings)
                int radius = data.cylinderRadius != null && data.cylinderRadius > 0 ? data.cylinderRadius : 10;
                int height = data.cylinderHeight != null && data.cylinderHeight > 0 ? data.cylinderHeight : 20;

                shape = new CylinderShape(centerX, centerY, centerZ, radius, height);
                player.sendMessage(ColorUtil.parse("&7Cylindre: centre=(" + centerX + "," + centerY + "," + centerZ + ") r=" + radius + " h=" + height));
            } else {
                // Création d'un cuboid depuis la sélection
                if (!SelectionHelper.hasValidSelection(player)) {
                    player.sendMessage(ColorUtil.parse("&cVeuillez d'abord definir une selection avec /pos1 et /pos2."));
                    return;
                }

                Optional<BoundingBox> boundsOpt = SelectionHelper.getSelectionAsBoundingBox(player);
                if (boundsOpt.isEmpty()) {
                    player.sendMessage(ColorUtil.parse("&cSelection invalide."));
                    return;
                }

                shape = new CuboidShape(boundsOpt.get());
            }

            // Utiliser la priorité si définie
            int priority = data.regionPriority != null ? data.regionPriority : 0;

            regionService.createRegion(name, worldName, shape, player.getUuid(), priority).join();
            player.sendMessage(ColorUtil.parse("&aRegion '" + name + "' creee avec succes!"));
            player.sendMessage(ColorUtil.parse("&7Type: " + shape.getShapeType() + " - Volume: " + shape.getVolume() + " blocs - Priorite: " + priority));

            // Réinitialiser le type de forme pour la prochaine création
            createShapeType = "cuboid";
        } catch (Exception e) {
            player.sendMessage(ColorUtil.parse("&cErreur: " + e.getMessage()));
        }
    }

    private void handleCreateGlobalAsync(Player player) {
        Optional<RegionImpl> existingOpt = regionService.getGlobalRegion(worldName);
        if (existingOpt.isPresent()) {
            player.sendMessage(ColorUtil.parse("&cUne region globale existe deja dans ce monde."));
            // Rafraîchir la page home
            refreshHomePage();
            return;
        }

        player.sendMessage(ColorUtil.parse("&7Creation de la region globale..."));

        // Créer en async sans bloquer
        regionService.getOrCreateGlobalRegion(worldName)
            .thenAccept(region -> {
                player.sendMessage(ColorUtil.parse("&aRegion globale creee pour '" + worldName + "'!"));
                player.sendMessage(ColorUtil.parse("&7Utilisez l'editeur pour configurer les flags par defaut."));
                // Rafraîchir la page home
                refreshHomePage();
            })
            .exceptionally(e -> {
                player.sendMessage(ColorUtil.parse("&cErreur: " + e.getMessage()));
                return null;
            });
    }

    private void refreshHomePage() {
        if (currentRef == null || currentStore == null) return;
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder event = new UIEventBuilder();
        showPage("home", cmd, event);
        sendUpdate(cmd, event, false);
    }

    private void handleSetFlag(String flagData, Player player) {
        String[] parts = flagData.split(":");
        if (parts.length < 2) return;

        String flagName = parts[0];
        String valueStr = parts[1];

        RegionFlag flag = RegionFlag.fromName(flagName);
        if (flag == null) {
            player.sendMessage(ColorUtil.parse("&cFlag invalide: " + flagName));
            return;
        }

        if (selectedRegionName == null) return;

        Optional<RegionImpl> regionOpt = regionService.getRegion(worldName, selectedRegionName).join();
        if (regionOpt.isEmpty()) return;

        RegionImpl region = regionOpt.get();
        String regionDisplayName = RegionService.isGlobalRegion(region) ? "Region Globale" : region.getName();

        if (valueStr.equals("clear")) {
            regionService.clearFlag(region, flag).join();
            player.sendMessage(ColorUtil.parse("&7[&b" + worldName + "&7] Region &e" + regionDisplayName + "&7: Flag &f" + flag.getName() + "&7 supprime"));
        } else {
            Object newValue = flag.parseValue(valueStr);
            if (newValue != null) {
                regionService.setFlag(region, flag, newValue).join();
                String displayValue = formatFlagValue(newValue);
                player.sendMessage(ColorUtil.parse("&a[&b" + worldName + "&a] Region &e" + regionDisplayName + "&a: &f" + flag.getName() + " &a= &f" + displayValue));
            } else {
                player.sendMessage(ColorUtil.parse("&cValeur invalide pour le flag " + flag.getName()));
                return;
            }
        }

        // Flags utilise appendInline, donc rebuild()
        currentPage = "flags";
        rebuild();
    }

    private void handleSetGroupFlag(String flagData, Player player) {
        // Format: "rankName:flagName:nextValue"
        String[] parts = flagData.split(":");
        if (parts.length < 3) return;

        String rankName = parts[0];
        String flagName = parts[1];
        String valueStr = parts[2];

        RegionFlag flag = RegionFlag.fromName(flagName);
        if (flag == null) {
            player.sendMessage(ColorUtil.parse("&cFlag invalide: " + flagName));
            return;
        }

        if (selectedRegionName == null) return;

        Optional<RegionImpl> regionOpt = regionService.getRegion(worldName, selectedRegionName).join();
        if (regionOpt.isEmpty()) return;

        RegionImpl region = regionOpt.get();
        String regionDisplayName = RegionService.isGlobalRegion(region) ? "Region Globale" : region.getName();

        if (valueStr.equals("clear")) {
            regionService.clearGroupFlag(region, rankName, flag).join();
            player.sendMessage(ColorUtil.parse("&7[&b" + worldName + "&7] Region &e" + regionDisplayName + "&7: Groupe &d" + rankName + "&7 flag &f" + flag.getName() + "&7 supprime"));
        } else {
            Object newValue = flag.parseValue(valueStr);
            if (newValue != null) {
                regionService.setGroupFlag(region, rankName, flag, newValue).join();
                String displayValue = formatFlagValue(newValue);
                player.sendMessage(ColorUtil.parse("&a[&b" + worldName + "&a] Region &e" + regionDisplayName + "&a: Groupe &d" + rankName + "&a &f" + flag.getName() + " &a= &f" + displayValue));
            } else {
                player.sendMessage(ColorUtil.parse("&cValeur invalide pour le flag " + flag.getName()));
                return;
            }
        }

        // Flags utilise appendInline, donc rebuild()
        currentPage = "flags";
        rebuild();
    }

    private void handleSetPlayerFlag(String flagData, Player player) {
        // Format: "playerUuid:flagName:nextValue"
        String[] parts = flagData.split(":");
        if (parts.length < 3) return;

        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(parts[0]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ColorUtil.parse("&cUUID joueur invalide"));
            return;
        }

        String flagName = parts[1];
        String valueStr = parts[2];

        RegionFlag flag = RegionFlag.fromName(flagName);
        if (flag == null) {
            player.sendMessage(ColorUtil.parse("&cFlag invalide: " + flagName));
            return;
        }

        if (selectedRegionName == null) return;

        Optional<RegionImpl> regionOpt = regionService.getRegion(worldName, selectedRegionName).join();
        if (regionOpt.isEmpty()) return;

        RegionImpl region = regionOpt.get();
        String regionDisplayName = RegionService.isGlobalRegion(region) ? "Region Globale" : region.getName();
        String targetPlayerName = selectedPlayerName != null ? selectedPlayerName : targetUuid.toString().substring(0, 8);

        if (valueStr.equals("clear")) {
            regionService.clearPlayerFlag(region, targetUuid, flag).join();
            player.sendMessage(ColorUtil.parse("&7[&b" + worldName + "&7] Region &e" + regionDisplayName + "&7: Joueur &6" + targetPlayerName + "&7 flag &f" + flag.getName() + "&7 supprime"));
        } else {
            Object newValue = flag.parseValue(valueStr);
            if (newValue != null) {
                regionService.setPlayerFlag(region, targetUuid, flag, newValue).join();
                String displayValue = formatFlagValue(newValue);
                player.sendMessage(ColorUtil.parse("&a[&b" + worldName + "&a] Region &e" + regionDisplayName + "&a: Joueur &6" + targetPlayerName + "&a &f" + flag.getName() + " &a= &f" + displayValue));
            } else {
                player.sendMessage(ColorUtil.parse("&cValeur invalide pour le flag " + flag.getName()));
                return;
            }
        }

        // Flags utilise appendInline, donc rebuild()
        currentPage = "flags";
        rebuild();
    }

    private void handleSearchPlayer(String playerName, Player player) {
        if (playerName == null || playerName.isBlank()) {
            player.sendMessage(ColorUtil.parse("&cVeuillez entrer un pseudo."));
            return;
        }

        try {
            var playerManager = IslandiumPlugin.get().getPlayerManager();
            var uuidOpt = playerManager.getPlayerUUID(playerName).join();

            if (uuidOpt.isEmpty()) {
                player.sendMessage(ColorUtil.parse("&cJoueur '" + playerName + "' non trouve."));
                selectedPlayerUuid = null;
                selectedPlayerName = null;
                return;
            }

            selectedPlayerUuid = uuidOpt.get();
            selectedPlayerName = playerName;
            playerNameCache.put(selectedPlayerUuid, playerName);
            player.sendMessage(ColorUtil.parse("&aJoueur '" + playerName + "' selectionne."));
        } catch (Exception e) {
            player.sendMessage(ColorUtil.parse("&cErreur: " + e.getMessage()));
            selectedPlayerUuid = null;
            selectedPlayerName = null;
        }
    }

    private String formatFlagValue(Object value) {
        if (Boolean.TRUE.equals(value)) return "ON";
        if (Boolean.FALSE.equals(value)) return "OFF";
        if ("members".equals(value)) return "MEMBRES";
        return value != null ? value.toString() : "---";
    }

    private void handleAddMember(PageData data, Player player, boolean asOwner) {
        if (selectedRegionName == null) return;

        String memberName = data.memberName;
        if (memberName == null || memberName.isBlank()) {
            player.sendMessage(ColorUtil.parse("&cVeuillez entrer un pseudo."));
            return;
        }

        Optional<RegionImpl> regionOpt = regionService.getRegion(worldName, selectedRegionName).join();
        if (regionOpt.isEmpty()) return;

        RegionImpl region = regionOpt.get();

        try {
            var playerManager = IslandiumPlugin.get().getPlayerManager();
            var uuidOpt = playerManager.getPlayerUUID(memberName).join();

            if (uuidOpt.isEmpty()) {
                player.sendMessage(ColorUtil.parse("&cJoueur '" + memberName + "' non trouve."));
                return;
            }

            UUID targetUuid = uuidOpt.get();
            playerNameCache.put(targetUuid, memberName);

            if (asOwner) {
                regionService.addOwner(region, targetUuid, player.getUuid()).join();
                player.sendMessage(ColorUtil.parse("&a" + memberName + " ajoute comme proprietaire."));
            } else {
                regionService.addMember(region, targetUuid, player.getUuid()).join();
                player.sendMessage(ColorUtil.parse("&a" + memberName + " ajoute comme membre."));
            }
        } catch (Exception e) {
            player.sendMessage(ColorUtil.parse("&cErreur: " + e.getMessage()));
        }
    }

    private void handleRemoveMember(String uuidStr, Player player, boolean isOwner) {
        if (selectedRegionName == null || uuidStr == null) return;

        Optional<RegionImpl> regionOpt = regionService.getRegion(worldName, selectedRegionName).join();
        if (regionOpt.isEmpty()) return;

        RegionImpl region = regionOpt.get();

        try {
            UUID targetUuid = UUID.fromString(uuidStr);
            if (isOwner) {
                regionService.removeOwner(region, targetUuid).join();
                player.sendMessage(ColorUtil.parse("&aProprietaire retire."));
            } else {
                regionService.removeMember(region, targetUuid).join();
                player.sendMessage(ColorUtil.parse("&aMembre retire."));
            }
        } catch (Exception e) {
            player.sendMessage(ColorUtil.parse("&cErreur: " + e.getMessage()));
        }
    }

    private void handleChangePriority(PageData data, Player player) {
        if (selectedRegionName == null) {
            player.sendMessage(ColorUtil.parse("&cAucune region selectionnee."));
            return;
        }

        if (data.newPriority == null) {
            player.sendMessage(ColorUtil.parse("&cVeuillez entrer une priorite valide."));
            return;
        }

        Optional<RegionImpl> regionOpt = regionService.getRegion(worldName, selectedRegionName).join();
        if (regionOpt.isEmpty()) {
            player.sendMessage(ColorUtil.parse("&cRegion introuvable."));
            return;
        }

        RegionImpl region = regionOpt.get();
        int newPriority = data.newPriority;
        int oldPriority = region.getPriority();

        try {
            regionService.setPriority(region, newPriority).join();
            player.sendMessage(ColorUtil.parse("&aPriorite de '" + region.getName() + "' changee: " + oldPriority + " -> " + newPriority));
        } catch (Exception e) {
            player.sendMessage(ColorUtil.parse("&cErreur lors du changement de priorite: " + e.getMessage()));
        }
    }

    private void handleUpdateFromSelection(Player player) {
        if (selectedRegionName == null) {
            player.sendMessage(ColorUtil.parse("&cAucune region selectionnee."));
            return;
        }

        Optional<RegionImpl> regionOpt = regionService.getRegion(worldName, selectedRegionName).join();
        if (regionOpt.isEmpty()) {
            player.sendMessage(ColorUtil.parse("&cRegion introuvable."));
            return;
        }

        RegionImpl region = regionOpt.get();

        // Vérifier que c'est bien un cuboid
        if (!(region.getShape() instanceof CuboidShape)) {
            player.sendMessage(ColorUtil.parse("&cCette region n'est pas un cuboid."));
            return;
        }

        // Obtenir la sélection du joueur
        Optional<BoundingBox> selectionOpt = SelectionHelper.getSelectionAsBoundingBox(player);
        if (selectionOpt.isEmpty()) {
            player.sendMessage(ColorUtil.parse("&cVous n'avez pas de selection active. Utilisez /pos1 et /pos2."));
            return;
        }

        BoundingBox selection = selectionOpt.get();

        // Créer une nouvelle forme cuboid avec la sélection
        CuboidShape newShape = new CuboidShape(selection);

        try {
            regionService.redefineRegion(region, newShape).join();
            player.sendMessage(ColorUtil.parse("&aForme de la region '" + region.getName() + "' mise a jour depuis votre selection."));
        } catch (Exception e) {
            player.sendMessage(ColorUtil.parse("&cErreur lors de la mise a jour de la forme: " + e.getMessage()));
        }
    }

    private void handleUpdateCylinder(PageData data, Player player) {
        plugin.log(java.util.logging.Level.INFO, "[DEBUG] handleUpdateCylinder called! selectedRegionName=" + selectedRegionName);
        plugin.log(java.util.logging.Level.INFO, "[DEBUG] newRadius=" + data.newRadius + ", newHeight=" + data.newHeight);

        if (selectedRegionName == null) {
            player.sendMessage(ColorUtil.parse("&cAucune region selectionnee."));
            return;
        }

        if (data.newRadius == null || data.newHeight == null) {
            player.sendMessage(ColorUtil.parse("&cVeuillez entrer un rayon et une hauteur valides."));
            return;
        }

        // Les valeurs sont déjà des Integer, pas besoin de parser
        int newRadius = data.newRadius;
        int newHeight = data.newHeight;

        plugin.log(java.util.logging.Level.INFO, "handleUpdateCylinder: Attempting to update cylinder with radius=" + newRadius + ", height=" + newHeight);

        Optional<RegionImpl> regionOpt = regionService.getRegion(worldName, selectedRegionName).join();
        if (regionOpt.isEmpty()) {
            player.sendMessage(ColorUtil.parse("&cRegion introuvable."));
            return;
        }

        RegionImpl region = regionOpt.get();

        // Vérifier que c'est bien un cylindre
        if (!(region.getShape() instanceof CylinderShape oldCyl)) {
            player.sendMessage(ColorUtil.parse("&cCette region n'est pas un cylindre."));
            return;
        }

        if (newRadius <= 0 || newHeight <= 0) {
            player.sendMessage(ColorUtil.parse("&cLe rayon et la hauteur doivent etre positifs."));
            return;
        }

        // Créer un nouveau cylindre avec les mêmes coordonnées de centre mais nouveau rayon/hauteur
        CylinderShape newShape = new CylinderShape(
            oldCyl.getCenterX(),
            oldCyl.getCenterY(),
            oldCyl.getCenterZ(),
            newRadius,
            newHeight,
            oldCyl.getRadiusAdjust()
        );

        try {
            regionService.redefineRegion(region, newShape).join();
            player.sendMessage(ColorUtil.parse("&aForme du cylindre '" + region.getName() + "' mise a jour: rayon=" + newRadius + ", hauteur=" + newHeight));
        } catch (Exception e) {
            player.sendMessage(ColorUtil.parse("&cErreur lors de la mise a jour du cylindre: " + e.getMessage()));
        }
    }

    // ===========================================
    // HELPERS
    // ===========================================

    /**
     * Affiche une ligne de flag dans la liste des flags actifs.
     * @param cmd Command builder
     * @param flag Le flag à afficher
     * @param value La valeur du flag
     * @param context Contexte (null pour flag de base, "G: rankName" pour groupe, "P: playerName" pour joueur)
     */
    private void displayFlagRow(UICommandBuilder cmd, RegionFlag flag, Object value, String context) {
        String valueStr;
        String bgColor;
        String textColor;

        if (value instanceof Boolean boolVal) {
            valueStr = boolVal ? "ALLOW" : "DENY";
            bgColor = boolVal ? "#1a3a1a" : "#3a1a1a";
            textColor = boolVal ? "#5adf5a" : "#df5a5a";
        } else if ("members".equals(value)) {
            valueStr = "MEMBRES";
            bgColor = "#2a2a1a";
            textColor = "#dfdf5a";
        } else {
            valueStr = value != null ? sanitize(value.toString()) : "null";
            bgColor = "#1a2a3a";
            textColor = "#6ab4ff";
        }

        // Nom du flag avec contexte si présent
        String flagDisplayName = formatFlagName(flag.getName());
        if (context != null && !context.isEmpty()) {
            flagDisplayName = flagDisplayName + " [" + sanitize(context) + "]";
        }

        String flagCode = String.format(
            "Group { Anchor: (Height: 32, Bottom: 3); LayoutMode: Left; Background: (Color: %s); Padding: (Horizontal: 10, Vertical: 4); " +
            "Label { FlexWeight: 1; Text: \"%s\"; Style: (FontSize: 11, TextColor: #e0e0e0, VerticalAlignment: Center); } " +
            "Label { Anchor: (Width: 70); Text: \"%s\"; Style: (FontSize: 11, TextColor: %s, HorizontalAlignment: Center, VerticalAlignment: Center); } }",
            bgColor, flagDisplayName, valueStr, textColor
        );

        cmd.appendInline("#ActiveFlagsList", flagCode);
    }

    private Player getPlayer() {
        if (currentRef == null || currentStore == null) return null;
        return currentStore.getComponent(currentRef, Player.getComponentType());
    }

    private String getPlayerName(UUID uuid) {
        if (playerNameCache.containsKey(uuid)) {
            return playerNameCache.get(uuid);
        }

        try {
            var playerManager = IslandiumPlugin.get().getPlayerManager();
            var nameOpt = playerManager.getPlayerName(uuid).join();
            if (nameOpt.isPresent()) {
                playerNameCache.put(uuid, nameOpt.get());
                return nameOpt.get();
            }
        } catch (Exception ignored) {}

        return uuid.toString().substring(0, 8) + "...";
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000) return String.format("%.1fM", number / 1_000_000.0);
        if (number >= 1_000) return String.format("%.1fK", number / 1_000.0);
        return String.valueOf(number);
    }

    private String formatFlagName(String flagName) {
        String formatted = flagName.replace("-", " ").replace("_", " ");
        String[] words = formatted.split(" ");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            if (!words[i].isEmpty()) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) result.append(words[i].substring(1).toLowerCase());
            }
        }
        return result.toString();
    }

    private static String sanitize(String input) {
        if (input == null) return "";
        // Remplacer les caractères problématiques pour le parser UI
        return input
            .replace("\"", "")
            .replace("\\", "")
            .replace("{", "")
            .replace("}", "")
            .replace(";", "")
            .replace("(", "")
            .replace(")", "")
            .replace("-", " ")
            .replace("_", " ")
            // Remplacer les accents par leurs équivalents sans accent
            .replace("é", "e")
            .replace("è", "e")
            .replace("ê", "e")
            .replace("ë", "e")
            .replace("à", "a")
            .replace("â", "a")
            .replace("ä", "a")
            .replace("ù", "u")
            .replace("û", "u")
            .replace("ü", "u")
            .replace("ô", "o")
            .replace("ö", "o")
            .replace("î", "i")
            .replace("ï", "i")
            .replace("ç", "c")
            .replace("'", " ");
    }

    // ===========================================
    // PAGE DATA
    // ===========================================

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .addField(new KeyedCodec<>("NavTo", Codec.STRING), (d, v) -> d.navTo = v, d -> d.navTo)
            .addField(new KeyedCodec<>("SelectRegion", Codec.STRING), (d, v) -> d.selectRegion = v, d -> d.selectRegion)
            .addField(new KeyedCodec<>("SetFlag", Codec.STRING), (d, v) -> d.setFlag = v, d -> d.setFlag)
            .addField(new KeyedCodec<>("RemoveMember", Codec.STRING), (d, v) -> d.removeMember = v, d -> d.removeMember)
            .addField(new KeyedCodec<>("RemoveOwner", Codec.STRING), (d, v) -> d.removeOwner = v, d -> d.removeOwner)
            .addField(new KeyedCodec<>("@RegionName", Codec.STRING), (d, v) -> d.regionName = v, d -> d.regionName)
            // Les NumberFields envoient des entiers, pas des strings
             .addField(new KeyedCodec<>("@RegionPriority", Codec.INTEGER), (d, v) -> d.regionPriority = v, d -> d.regionPriority)
            .addField(new KeyedCodec<>("@MemberName", Codec.STRING), (d, v) -> d.memberName = v, d -> d.memberName)
            .addField(new KeyedCodec<>("@CylinderRadius", Codec.INTEGER), (d, v) -> d.cylinderRadius = v, d -> d.cylinderRadius)
            .addField(new KeyedCodec<>("@CylinderHeight", Codec.INTEGER), (d, v) -> d.cylinderHeight = v, d -> d.cylinderHeight)
            .addField(new KeyedCodec<>("@NewPriority", Codec.INTEGER), (d, v) -> d.newPriority = v, d -> d.newPriority)
            .addField(new KeyedCodec<>("@NewRadius", Codec.INTEGER), (d, v) -> d.newRadius = v, d -> d.newRadius)
            .addField(new KeyedCodec<>("@NewHeight", Codec.INTEGER), (d, v) -> d.newHeight = v, d -> d.newHeight)
            // Nouveaux champs pour les onglets flags
            .addField(new KeyedCodec<>("FlagsTab", Codec.STRING), (d, v) -> d.flagsTab = v, d -> d.flagsTab)
            .addField(new KeyedCodec<>("SelectRank", Codec.STRING), (d, v) -> d.selectRank = v, d -> d.selectRank)
            .addField(new KeyedCodec<>("SelectPlayer", Codec.STRING), (d, v) -> d.selectPlayer = v, d -> d.selectPlayer)
            .addField(new KeyedCodec<>("SetGroupFlag", Codec.STRING), (d, v) -> d.setGroupFlag = v, d -> d.setGroupFlag)
            .addField(new KeyedCodec<>("SetPlayerFlag", Codec.STRING), (d, v) -> d.setPlayerFlag = v, d -> d.setPlayerFlag)
            .addField(new KeyedCodec<>("@PlayerSearch", Codec.STRING), (d, v) -> d.playerSearch = v, d -> d.playerSearch)
            .build();

        public String action;
        public String navTo;
        public String selectRegion;
        public String setFlag;
        public String removeMember;
        public String removeOwner;
        public String regionName;
        public Integer regionPriority;  // Changé de String à Integer
        public String memberName;
        public Integer cylinderRadius;  // Changé de String à Integer
        public Integer cylinderHeight;  // Changé de String à Integer
        public Integer newPriority;  // Nouvelle priorité à définir
        public Integer newRadius;  // Nouveau rayon pour cylindre
        public Integer newHeight;  // Nouvelle hauteur pour cylindre
        // Nouveaux champs pour les onglets flags
        public String flagsTab;
        public String selectRank;
        public String selectPlayer;
        public String setGroupFlag;
        public String setPlayerFlag;
        public String playerSearch;
    }
}
