# VN Chest Addon (Meteor Client, Fabric 1.21.4)

Addon cho [Meteor Client](https://meteorclient.com/) dua tren [meteor-addon-template](https://github.com/MeteorDevelopment/meteor-addon-template).
Yeu cau: Fabric Loader + Meteor Client (build cho **Minecraft 1.21.4**). Litematica la **tuy chon** (soft-dependency)
- neu khong cai, addon van load binh thuong, chi module `Auto Collect` se khong lam gi ca.

## 2 module

### `ChestAddon > chest-tracker`
- Moi lan ban mo 1 cai ruong (Chest/Trapped Chest/Barrel/Shulker Box), addon ghi lai (trong RAM, khong luu file)
  danh sach item dang co trong ruong do.
- Ruong da mo se duoc ve outline "phat sang" ngoai the gioi - **ke ca 2 nua cua rương doi** (mo 1 nua, ca 2 nua
  deu sang).
- Bat/tat module, hoac roi + vao lai the gioi -> du lieu bi xoa sach (reset), dung nhu yeu cau.

### `ChestAddon > auto-collect`
- Doc "Material List" dang active cua Litematica (chinh la material list hien trong info-hub / man hinh
  Material List cua schematic ban dang dat) de biet con thieu nguyen lieu gi, bao nhieu.
- Khi ban mo 1 ruong (nen la ruong dang sang o tren), moi tick module tu shift-click 1 stack item **khop voi
  nguyen lieu con thieu** vao tui do. Item khong nam trong danh sach thieu -> bo qua hoan toan.
- Tu dung khi: tui do (hotbar + 27 o chinh) het cho trong, HOAC (neu ban bat setting `tu-tat-khi-du-nguyen-lieu`)
  khi Litematica bao da du tat ca nguyen lieu.

### Gioi han quan trong (doc truoc khi dung)
Module `auto-collect` **khong tu di chuyen nhan vat** den tung ruong - khong co pathfinding/Baritone di kem.
Ban van phai tu di toi va mo tung ruong bang tay (uu tien ruong dang phat sang tu `chest-tracker`); moi lan mo
ra, module se tu dong "hut" dung phan nguyen lieu con thieu. Neu muon toan bo flow tu dong 100% (tu tim duong,
tu mo, tu dong nhieu ruong lien tuc) se can them mot module pathfinding rieng - chua co trong ban nay.

## Build

### Cach 1: Build tren may ban (can Internet, khong bi chan maven.fabricmc.net / maven.meteordev.org)

```bash
./gradlew build
```

File jar ket qua nam trong `build/libs/meteor-obot-1.0.jar`. Bo vao thu muc `mods/` cung voi Fabric API,
Fabric Loader va Meteor Client (va Litematica neu muon dung module 2).

### Cach 2: Build tren GitHub Actions (khong can Internet o may ban)

1. Tao 1 repo GitHub moi, push toan bo thu muc nay len (bao gom ca thu muc `libs/*.jar` - **dung xoa 2 file
   jar nay**, chung duoc dung de compile-only, khong bi dong goi vao jar cuoi cung).
2. Workflow `.github/workflows/build.yml` da co san se tu chay `./gradlew build` moi lan push.
3. Vao tab **Actions** tren GitHub -> chon lan chay moi nhat -> tai file jar trong phan **Artifacts**
   (ten `vn-chest-addon`).
4. Neu push len nhanh `main`/`master`, workflow con tu tao 1 GitHub Release ten "Dev Build" (tag `latest`) kem
   san file jar de tai truc tiep tu tab **Releases**, khong can vao Actions.

> Luu y: sua ten nhanh trong `on.push.branches` cua `build.yml` neu repo ban dung nhanh khac `main`/`master`.

## Cau truc thu muc

```
chest-addon/
├── build.gradle            # cau hinh build, tro toi Meteor maven + libs/ (litematica, malilib)
├── gradle.properties       # version MC 1.21.4, yarn mappings, ten mod
├── libs/                   # jar Litematica + MaliLib (chi de compile, khong bundle)
├── .github/workflows/
│   └── build.yml           # GitHub Action: build + upload artifact + tao release "latest"
└── src/main/
    ├── java/com/vnaddon/chest/
    │   ├── ChestAddon.java             # entrypoint chinh
    │   ├── litematica/
    │   │   ├── LitematicaCompat.java   # wrapper an toan (check mod co cai khong)
    │   │   └── LitematicaAccess.java   # goi truc tiep API cua Litematica
    │   └── modules/
    │       ├── ChestTrackerModule.java
    │       └── AutoCollectModule.java
    └── resources/fabric.mod.json
```

## Nhung thu ban nen tu kiem tra lai khi build that (danh cho ai muon sua/mo rong code)

Toan bo API trong code nay duoc doi chieu truc tiep voi source cua `meteor-client` nhanh 1.21.4 va bytecode
that cua 2 file jar ban upload (dung `strings`/`unzip` de doc ten class/method vi moi truong nay khong co
`javap`/mang de tai Fabric maven), nen do chinh xac kha cao. Tuy nhien addon **chua duoc bien dich thu** (sandbox
nay khong co quyen truy cap maven.fabricmc.net / maven.meteordev.org), nen lan build dau tien tren may ban hoac
GitHub Actions co the con vai loi cu phap nho can sua - neu gap loi, gui lai log bien dich, minh se sua tiep.
