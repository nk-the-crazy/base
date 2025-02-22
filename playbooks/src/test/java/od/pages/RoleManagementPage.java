package od.pages;

import net.serenitybdd.core.annotations.findby.FindBy;
import net.serenitybdd.core.pages.PageObject;
import net.serenitybdd.core.pages.WebElementFacade;

public class RoleManagementPage extends PageObject {

    //region WEB ELEMENTS: Fields

    //endregion

    //region WEB ELEMENTS: Buttons

    @FindBy(xpath = "*//a[@class=\"b-btn b-btn_green b-btn_search-field-level\"]")
    public WebElementFacade buttonAddRole;
    //endregion

    //region WEB ELEMENTS: Checkboxes

    //endregion

    //region WEB ELEMENTS: Links

    @FindBy(id = "subt_link__create-peer")
    public WebElementFacade buttonCreatePeer;

    //endregion

    //region WEB ELEMENTS: Tables

    //endregion

    //region WEB ELEMENTS: Pickers

    //endregion

    //region WEB ELEMENTS: Selectors

    //endregion

    //region WEB ELEMENTS: Images

    //endregion

    //region WEB ELEMENTS: Icons

    //endregion

    //region WEB ELEMENTS: Headers

    @FindBy(xpath = "*//h1[contains(text(),\"Role management\")]")
    public WebElementFacade headerRoleManagement;

    //endregion
}