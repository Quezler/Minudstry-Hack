package Photon.UI;

import Photon.gae;
import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.scene.Group;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Interval;
import arc.util.Scaling;
import arc.util.Strings;
import arc.util.Structs;
import mindustry.core.GameState.State;
import mindustry.entities.type.Unit;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.net.NetConnection;
import mindustry.net.Packets.AdminAction;
import mindustry.ui.Styles;
import mindustry.ui.fragments.Fragment;

import static mindustry.Vars.*;

public class PlayerListFragment extends Fragment {
    private boolean visible = false;
    private Table content = new Table().marginRight(13f).marginLeft(13f);
    private Interval timer = new Interval();
    private TextField sField;

    @Override
    public void build(Group parent){
        parent.fill(cont -> {
            cont.visible(() -> visible);
            cont.update(() -> {
                if(!(net.active() && !state.is(State.menu))){
                    visible = false;
                    return;
                }

                if(visible && timer.get(20)){
                    rebuild();
                    content.pack();
                    content.act(Core.graphics.getDeltaTime());
                    //TODO hack
                    Core.scene.act(0f);
                }
            });

            cont.table(Tex.buttonTrans, pane -> {
                pane.label(() -> Core.bundle.format(playerGroup.size() == 1 ? "players.single" : "players", playerGroup.size()));
                pane.row();
                sField = pane.addField(null, text -> {
                    rebuild();
                }).grow().pad(8).get();
                sField.setMaxLength(maxNameLength);
                sField.setMessageText(Core.bundle.format("players.search"));
                pane.row();
                pane.pane(content).grow().get().setScrollingDisabled(true, false);
                pane.row();

                pane.table(menu -> {
                    menu.defaults().growX().height(50f).fillY();

                    menu.addButton("$server.bans", ui.bans::show).disabled(b -> net.client());
                    menu.addButton("$server.admins", ui.admins::show).disabled(b -> net.client());
                    menu.addButton("$close", this::toggle);
                }).margin(0f).pad(10f).growX();

            }).touchable(Touchable.enabled).margin(14f);
        });

        rebuild();
    }

    public void rebuild(){
        content.clear();

        float h = 74f;

        playerGroup.all().sort(Structs.comparing(Unit::getTeam));
        playerGroup.all().each(user -> {
            NetConnection connection = user.con;

            if(connection == null && net.server() && !user.isLocal) return;
            if(sField.getText().length() > 0 && !user.name.toLowerCase().contains(sField.getText().toLowerCase()) && !Strings.stripColors(user.name.toLowerCase()).contains(sField.getText().toLowerCase())) return;

            Table button = new Table();
            button.left();
            button.margin(5).marginBottom(10);

            Table table = new Table(){
                @Override
                public void draw(){
                    super.draw();
                    Draw.color(Pal.gray);
                    Draw.alpha(parentAlpha);
                    Lines.stroke(Scl.scl(4f));
                    Lines.rect(x, y, width, height);
                    Draw.reset();
                }
            };
            table.margin(8);
            table.add(new Image(user.getIconRegion()).setScaling(Scaling.none)).grow();

            button.add(table).size(h);
            button.labelWrap("[#" + user.color.toString().toUpperCase() + "]" + user.name).width(170f).pad(10);
            button.add().grow();

            button.addImage(Icon.admin).visible(() -> user.isAdmin && !(!user.isLocal && net.server())).padRight(5).get().updateVisibility();

            if((net.server() || player.isAdmin) && !user.isLocal && (!user.isAdmin || net.server())){
                button.add().growY();

                float bs = (h) / 2f;

                button.table(t -> {
                    t.defaults().size(bs);

                    t.addImageButton(Icon.hammer, Styles.clearPartiali,
                            () -> ui.showConfirm("$confirm", Core.bundle.format("confirmban",  user.name), () -> Call.onAdminRequest(user, AdminAction.ban)));
                    t.addImageButton(Icon.cancel, Styles.clearPartiali,
                            () -> ui.showConfirm("$confirm", Core.bundle.format("confirmkick",  user.name), () -> Call.onAdminRequest(user, AdminAction.kick)));

                    t.row();

                    t.addImageButton(Icon.admin, Styles.clearTogglePartiali, () -> {
                        if(net.client()) return;

                        String id = user.uuid;

                        if(netServer.admins.isAdmin(id, connection.address)){
                            ui.showConfirm("$confirm", Core.bundle.format("confirmunadmin",  user.name), () -> netServer.admins.unAdminPlayer(id));
                        }else{
                            ui.showConfirm("$confirm", Core.bundle.format("confirmadmin",  user.name), () -> netServer.admins.adminPlayer(id, user.usid));
                        }
                    })
                            .update(b -> b.setChecked(user.isAdmin))
                            .disabled(b -> net.client())
                            .touchable(() -> net.client() ? Touchable.disabled : Touchable.enabled)
                            .checked(user.isAdmin);

                    t.addImageButton(Icon.zoom, Styles.clearPartiali, () -> Call.onAdminRequest(user, AdminAction.trace));

                }).padRight(12).size(bs + 10f, bs);
            }else if(!user.isLocal && !user.isAdmin && net.client() && playerGroup.size() >= 3 && player.getTeam() == user.getTeam()){ //votekick
                button.add().growY();

                button.addImageButton(Icon.hammer, Styles.clearPartiali,
                        () -> ui.showConfirm("$confirm", Core.bundle.format("confirmvotekick",  user.name), () -> Call.sendChatMessage("/votekick " + user.name))).size(h);
            }

                button.row();
                button.addImageButton(Icon.commandRally, Styles.clearPartiali, () -> ui.showConfirm("Assist", "Are you sure want to assist the player ?", () -> gae.commandCenter.tellAllSlave("ASSIST " + user.name)));
                button.addImageButton(Icon.book, Styles.clearPartiali, () -> ui.showConfirm("Report", "Are you sure want to report this player", () -> gae.reporter.addTargetPlayer(user)));

            content.add(button).padBottom(-6).width(350f).maxHeight(h + 14);
            content.row();
            content.addImage().height(4f).color(state.rules.pvp ? user.getTeam().color : Pal.gray).growX();
            content.row();
        });

        if(sField.getText().length() > 0 && !playerGroup.all().contains(user -> user.name.toLowerCase().contains(sField.getText().toLowerCase()))) {
            content.add(Core.bundle.format("players.notfound")).padBottom(6).width(350f).maxHeight(h + 14);
        }

        content.marginBottom(5);
    }

    public void toggle(){
        visible = !visible;
        if(visible){
            rebuild();
        }else{
            Core.scene.setKeyboardFocus(null);
            sField.clearText();
        }
    }

}
