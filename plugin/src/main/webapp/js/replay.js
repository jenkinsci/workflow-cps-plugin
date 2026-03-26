/**
 * Attaches click behavior to "Replay" buttons to trigger a POST
 * request and show a success or error notification.
 */
Behaviour.specify(
    "button[data-type='replay']",
    "button-replay",
    999,
    (button) => {
        button.addEventListener("click", function (event) {
            let success = button.dataset.buildSuccess;
            let failure = button.dataset.buildFailure;

            fetch(button.dataset.baseUrl + button.dataset.href, {
                method: "post",
                headers: crumb.wrap({}),
            }).then((rsp) => {
                if (rsp.status === 200) {
                    notificationBar.show(success, notificationBar.SUCCESS);
                } else {
                    notificationBar.show(failure, notificationBar.ERROR);
                }
            });
            event.preventDefault();
        });
    },
);
