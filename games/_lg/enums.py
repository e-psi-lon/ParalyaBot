from enum import Enum

from shared.enums import Users


class Channels(Enum):
    LOUP_CHAT = 731835704782487592
    LOUP_VOTE = 1184844302841553067
    LOUP_CITROUILLEUR = 902134060669890590
    LOUP_INFECT = 711974489847758939
    SALVATEUR = 711988655899541517
    VOYANTE = 711976475665891416
    RENARD = 711976671439224893
    SORCIERE = 711988495287189705
    CORBEAU = 712375326298341427
    VAMPIRE = 902133207602958346
    FACTEUR = 731839290295713792
    FAUCHEUSE = 731836144517513217
    CHASSEUR = 711988140096880771
    ENFANT_SAUVAGE = 731168097238646935
    FANTOME = 864963910540329010
    MONTREUR_DOURS = 711986888747253861
    DETECTIVE = 711985958576324701
    JUGE = 732260636175892481
    COUPLE = 731836561372741693
    SOEURS = 731836826255360050
    CUPIDON = 711987800005673052
    VOLEUR = 732179783483654145
    DICTATEUR = 732260947573604352
    CLOWN = 1167587030067327076
    ANGE = 711987073900347502
    PETITE_FILLE = 711987258340671590
    LOUP_BLANC = 711973901701349446
    LOUP_BAVARD = 731837296156082186
    LOUP_ANONYME = 1050194835019157555
    COMMISSAIRE = 902663936892076033
    CHAMAN = 902662892871094292
    ORACLE = 921862617977475112
    GRAND_MECHANT_LUTIN = 711974646362144879
    LUTIN_SOPORIFIQUE = 902133539942854686
    CROQUEMORT = 921863209047183391
    BERGER = 711976842659233933
    TELEPATHE = 1050184397447503982
    LOUP_FRUSTRE = 902133053462302770
    SAVANT_FOU = 864837112690901012
    ANCIEN = 731834915183656982
    CONFESSEUR = 732180904021065789


class GameRoles(Enum):
    LOUP = "Loup"
    LOUP_BLANC = "Loup Blanc"
    LOUP_ANONYME = "Loup Anonyme"
    LOUP_INFECT = "Loup Infect"
    LOUP_BAVARD = "Loup Bavard"
    LOUP_FRUSTRE = "Loup Frustré"
    GRAND_MECHANT_LOUP = "Grand Méchant Loup"


class GlobalChannel(Enum):
    ANNONCES_VILLAGE = 1207671762171731978
    VILLAGE = 1209307556808233021
    VOTE = 1209585425744793630
    SUJET = 1184460057107255326
    RESUME = 1210707621263310908


class Roles(Enum):
    LG_VIVANT = 709834794707714141
    LG_MORT = 709756843768938496


class AdminChannel(Enum):
    MP = 1184885725221625866


class VoteType(Enum):
    VILLAGE = 0
    LG = 1
